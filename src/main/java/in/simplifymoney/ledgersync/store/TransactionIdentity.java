package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.Locale;

final class TransactionIdentity {
    private TransactionIdentity() {}
    static String of(NormalizedTxn t) {
        return t.accountLast4() + "|" + t.occurredAt() + "|" + t.direction().name() + "|"
                + t.amount().setScale(2).toPlainString() + "|" + merchant(t.merchant());
    }
    private static String merchant(String value) {
        return value == null ? "" : value.trim().replaceAll("\s+", " ").toUpperCase(Locale.ROOT);
    }
}