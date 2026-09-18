package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

public final class DocumentStoreBenchmark {
    private DocumentStoreBenchmark() {}
    public static void main(String[] args) {
        try (MongoDocumentStore store = new MongoDocumentStore()) {
            store.clearAll();
            for (int i = 0; i < 100_000; i++) store.save(sample(i));
            var q1 = store.explainAccountMonth("4821", YearMonth.of(2026, 6));
            var q2 = store.explainCategoryTotals("4821");
            var q3 = store.explainByMessageId("bench-099999");
            System.out.println("100,000 transaction benchmark");
            print("Q1 account + month + newest first", q1);
            print("Q2 account category totals", q2);
            print("Q3 message id -> transaction", q3);
        }
    }
    private static void print(String label, MongoDocumentStore.QueryMetrics m) {
        System.out.printf("%-38s examined=%d returned=%d%n", label, m.totalDocsExamined(), m.nReturned());
    }
    private static NormalizedTxn sample(int i) {
        String account = (i % 2 == 0) ? "4821" : "9075";
        Category category = Category.values()[i % Category.values().length];
        Direction direction = (category == Category.INCOME || (category == Category.TRANSFER && i % 2 == 0))
                ? Direction.CREDIT : Direction.DEBIT;
        OffsetDateTime at = OffsetDateTime.of(2026, 1 + (i % 12), 1 + (i % 27),
                9 + (i % 10), i % 60, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        BigDecimal amount = BigDecimal.valueOf((i % 5000) + 1, 2).setScale(2);
        return new NormalizedTxn(account, at, direction, amount, category,
                "BENCH/" + (i % 100), List.of("bench-%06d".formatted(i)));
    }
}