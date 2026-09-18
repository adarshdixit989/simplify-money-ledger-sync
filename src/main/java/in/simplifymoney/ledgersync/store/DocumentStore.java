package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DocumentStore extends AutoCloseable {
    List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month);
    Map<Category, BigDecimal> categoryTotals(String accountLast4);
    Optional<NormalizedTxn> byMessageId(String messageId);
    void save(NormalizedTxn txn);
    default List<NormalizedTxn> all() { throw new UnsupportedOperationException("full document scan is only for consistency checks"); }
    default void rebuildCategoryTotals() { }
    @Override default void close() { }
}