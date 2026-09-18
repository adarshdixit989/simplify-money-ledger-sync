package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Backfill {
    private final SqlLedgerStore source;
    private final DocumentStore target;
    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }
    public Result run() {
        List<NormalizedTxn> rows = source.all();
        Map<String, NormalizedTxn> unique = new LinkedHashMap<>();
        long duplicateRows = 0;
        for (NormalizedTxn txn : rows) {
            String key = TransactionIdentity.of(txn);
            if (unique.putIfAbsent(key, txn) != null) duplicateRows++;
        }
        unique.values().forEach(target::save);
        return new Result(rows.size(), unique.size(), duplicateRows);
    }
    public record Result(long read, long written, long duplicateRows) {}
}