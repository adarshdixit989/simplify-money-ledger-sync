package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.List;

public interface LedgerStore {
    void save(NormalizedTxn txn);
    List<NormalizedTxn> all();
    long count();
}