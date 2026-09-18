package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConsistencyChecker {
    private final SqlLedgerStore sql;
    private final DocumentStore documents;
    public ConsistencyChecker(SqlLedgerStore sql, DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }
    public List<Divergence> check() {
        Map<String, NormalizedTxn> sqlRows = canonical(sql.all());
        Map<String, NormalizedTxn> docRows = canonical(documents.all());
        List<Divergence> out = new ArrayList<>();
        for (Map.Entry<String, NormalizedTxn> e : sqlRows.entrySet()) {
            NormalizedTxn s = e.getValue();
            NormalizedTxn d = docRows.get(e.getKey());
            if (d == null) {
                out.add(new Divergence("missing transaction " + e.getKey(), compact(s), "<missing>"));
                continue;
            }
            compare(out, e.getKey(), "accountLast4", s.accountLast4(), d.accountLast4());
            compare(out, e.getKey(), "occurredAt", s.occurredAt().toString(), d.occurredAt().toString());
            compare(out, e.getKey(), "direction", s.direction().name(), d.direction().name());
            compare(out, e.getKey(), "amount", s.amount().toPlainString(), d.amount().toPlainString());
            compare(out, e.getKey(), "category", s.category().name(), d.category().name());
            compare(out, e.getKey(), "merchant", s.merchant(), d.merchant());
            compare(out, e.getKey(), "sourceMessageIds", s.sourceMessageIds().toString(), d.sourceMessageIds().toString());
        }
        for (Map.Entry<String, NormalizedTxn> e : docRows.entrySet()) {
            if (!sqlRows.containsKey(e.getKey()))
                out.add(new Divergence("extra transaction " + e.getKey(), "<missing>", compact(e.getValue())));
        }
        return List.copyOf(out);
    }
    private static void compare(List<Divergence> out, String key, String field, String sql, String doc) {
        if (!Objects.equals(sql, doc)) out.add(new Divergence(key + "." + field, sql, doc));
    }
    private static Map<String, NormalizedTxn> canonical(List<NormalizedTxn> rows) {
        Map<String, NormalizedTxn> out = new LinkedHashMap<>();
        for (NormalizedTxn row : rows) out.putIfAbsent(TransactionIdentity.of(row), row);
        return out;
    }
    private static String compact(NormalizedTxn t) {
        return "account=" + t.accountLast4() + ", occurredAt=" + t.occurredAt()
                + ", direction=" + t.direction() + ", amount=" + t.amount()
                + ", category=" + t.category() + ", merchant=" + t.merchant()
                + ", sourceMessageIds=" + t.sourceMessageIds();
    }
    public record Divergence(String what, String inSql, String inDocuments) {}
}