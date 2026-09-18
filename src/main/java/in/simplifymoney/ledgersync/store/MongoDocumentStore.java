package in.simplifymoney.ledgersync.store;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.model.Updates;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.Decimal128;

public final class MongoDocumentStore implements DocumentStore, LedgerStore, AutoCloseable {
    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String DEFAULT_DATABASE = "ledger_sync";
    private final MongoClient client;
    private final MongoDatabase database;
    private final MongoCollection<Document> transactions;
    private final MongoCollection<Document> categoryTotals;

    public MongoDocumentStore() {
        this(System.getenv().getOrDefault("MONGO_URI", DEFAULT_URI),
                System.getenv().getOrDefault("MONGO_DATABASE", DEFAULT_DATABASE));
    }
    public MongoDocumentStore(String uri, String databaseName) {
        this.client = MongoClients.create(uri);
        this.database = client.getDatabase(databaseName);
        this.transactions = database.getCollection("transactions");
        this.categoryTotals = database.getCollection("category_totals");
        ensureIndexes();
    }
    private void ensureIndexes() {
        transactions.createIndex(Indexes.ascending("accountLast4", "occurredAt"),
                new IndexOptions().name("account_month_newest"));
        transactions.createIndex(Indexes.ascending("sourceMessageIds"),
                new IndexOptions().name("message_to_transaction").unique(false));
        categoryTotals.createIndex(Indexes.ascending("_id"),
                new IndexOptions().name("account_category_totals"));
    }
    @Override public List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        FindIterable<Document> docs = transactions.find(Filters.and(
                Filters.eq("accountLast4", accountLast4),
                Filters.gte("occurredAt", start.toString()),
                Filters.lt("occurredAt", end.toString())))
                .sort(Sorts.descending("occurredAt"));
        List<NormalizedTxn> out = new ArrayList<>();
        for (Document doc : docs) out.add(fromDocument(doc));
        return List.copyOf(out);
    }
    @Override public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Document doc = categoryTotals.find(Filters.eq("_id", accountLast4)).projection(Projections.excludeId()).first();
        Map<Category, BigDecimal> out = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            Object raw = doc == null ? null : doc.get(c.name());
            out.put(c, raw instanceof Decimal128 d ? d.bigDecimalValue().setScale(2) : BigDecimal.ZERO.setScale(2));
        }
        return out;
    }
    @Override public Optional<NormalizedTxn> byMessageId(String messageId) {
        return Optional.ofNullable(transactions.find(Filters.eq("sourceMessageIds", messageId)).first()).map(this::fromDocument);
    }
    @Override public void save(NormalizedTxn txn) {
        String key = TransactionIdentity.of(txn);
        UpdateResult result = transactions.updateOne(Filters.eq("_id", key),
            Updates.combine(
                Updates.setOnInsert("accountLast4", txn.accountLast4()),
                Updates.setOnInsert("occurredAt", txn.occurredAt().toString()),
                Updates.setOnInsert("direction", txn.direction().name()),
                Updates.setOnInsert("amount", new Decimal128(txn.amount())),
                Updates.setOnInsert("category", txn.category().name()),
                Updates.setOnInsert("merchant", txn.merchant()),
                Updates.addEachToSet("sourceMessageIds", txn.sourceMessageIds())),
            new UpdateOptions().upsert(true));
        if (result.getUpsertedId() != null) {
            categoryTotals.updateOne(Filters.eq("_id", txn.accountLast4()),
                new Document("$inc", new Document(txn.category().name(), new Decimal128(txn.amount()))),
                new UpdateOptions().upsert(true));
        }
    }
    @Override public List<NormalizedTxn> all() {
        List<NormalizedTxn> out = new ArrayList<>();
        for (Document d : transactions.find()) out.add(fromDocument(d));
        return List.copyOf(out);
    }
    @Override public long count() { return transactions.countDocuments(); }
    public long transactionCount() { return count(); }
    public QueryMetrics explainAccountMonth(String accountLast4, YearMonth month) {
        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        Document filter = new Document("accountLast4", accountLast4)
            .append("occurredAt", new Document("$gte", start.toString()).append("$lt", end.toString()));
        return extractMetrics(database.runCommand(new Document("explain",
            new Document("find", "transactions").append("filter", filter)
                .append("sort", new Document("occurredAt", -1)))));
    }
    public QueryMetrics explainCategoryTotals(String accountLast4) {
        return extractMetrics(database.runCommand(new Document("explain",
            new Document("find", "category_totals").append("filter", new Document("_id", accountLast4)))));
    }
    public QueryMetrics explainByMessageId(String messageId) {
        return extractMetrics(database.runCommand(new Document("explain",
            new Document("find", "transactions").append("filter", new Document("sourceMessageIds", messageId)))));
    }
    private QueryMetrics extractMetrics(Document explain) {
        Document executionStats = explain.get("executionStats", Document.class);
        if (executionStats == null) return new QueryMetrics(-1, -1, explain.toJson());
        return new QueryMetrics(executionStats.getInteger("totalDocsExamined", -1),
                executionStats.getInteger("nReturned", -1), explain.toJson());
    }
    @SuppressWarnings("unchecked")
    private NormalizedTxn fromDocument(Document d) {
        return new NormalizedTxn(d.getString("accountLast4"), OffsetDateTime.parse(d.getString("occurredAt")),
                Direction.valueOf(d.getString("direction")), d.get("amount", Decimal128.class).bigDecimalValue().setScale(2),
                Category.valueOf(d.getString("category")), d.getString("merchant"),
                (List<String>) d.get("sourceMessageIds"));
    }
    public void clearAll() { transactions.deleteMany(new Document()); categoryTotals.deleteMany(new Document()); }
    @Override public void rebuildCategoryTotals() {
        categoryTotals.deleteMany(new Document());
        for (Document d : transactions.find()) {
            NormalizedTxn t = fromDocument(d);
            categoryTotals.updateOne(Filters.eq("_id", t.accountLast4()),
                new Document("$inc", new Document(t.category().name(), new Decimal128(t.amount()))),
                new UpdateOptions().upsert(true));
        }
    }
    @Override public void close() { client.close(); }
    public record QueryMetrics(long totalDocsExamined, long nReturned, String explainJson) {}
}