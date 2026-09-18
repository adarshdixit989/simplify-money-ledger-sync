package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;

public final class IngestService {
    private final Parsers parsers; private final LedgerStore store;
    public IngestService(Parsers parsers, LedgerStore store){this.parsers=parsers;this.store=store;}
    public Stats ingestFile(Path corpus)throws IOException{
        List<RawMessage> messages=readCorpus(corpus); List<ParsedTxn> parsed=new ArrayList<>(); int skipped=0;
        for(RawMessage m:messages){Optional<ParsedTxn> p=parsers.parse(m); if(p.isEmpty()) skipped++; else parsed.add(p.get());}
        List<NormalizedTxn> transactions=normalize(parsed); Set<TransactionKey> existing=new HashSet<>();
        for(NormalizedTxn txn:store.all()) existing.add(TransactionKey.of(txn));
        int written=0; for(NormalizedTxn txn:transactions) if(existing.add(TransactionKey.of(txn))){store.save(txn);written++;}
        return new Stats(messages.size(),written,skipped,parsed.size()-transactions.size());
    }
    public static List<RawMessage> readCorpus(Path corpus)throws IOException{
        List<RawMessage> out=new ArrayList<>();
        try(Stream<String> lines=Files.lines(corpus)){for(String line:(Iterable<String>)lines.filter(s->!s.isBlank())::iterator){
            Map<String,Object> o=Json.parseObject(line);
            out.add(new RawMessage((String)o.get("message_id"),(String)o.get("channel"),(String)o.get("sender"),
                OffsetDateTime.parse((String)o.get("received_at")),(String)o.get("device_id"),(String)o.get("body")));}}
        return out;
    }
    public static List<NormalizedTxn> normalize(List<ParsedTxn> parsed){
        Map<TransactionKey,List<ParsedTxn>> grouped=new HashMap<>();
        for(ParsedTxn p:parsed) grouped.computeIfAbsent(TransactionKey.of(p),ignored->new ArrayList<>()).add(p);
        List<CanonicalTxn> canonical=grouped.values().stream().map(IngestService::canonicalise)
            .sorted(Comparator.comparing(CanonicalTxn::occurredAt).thenComparing(CanonicalTxn::accountLast4)
            .thenComparing(CanonicalTxn::sourceMessageId)).toList();
        Set<Integer> transfers=findTransfers(canonical); List<NormalizedTxn> out=new ArrayList<>(canonical.size());
        for(int i=0;i<canonical.size();i++){CanonicalTxn p=canonical.get(i); Category c;
            if(transfers.contains(i)) c=Category.TRANSFER;
            else if(p.direction()==Direction.DEBIT&&p.amount().compareTo(new BigDecimal("100.00"))<=0&&isUpi(p.merchant())) c=Category.MICRO;
            else c=p.direction()==Direction.DEBIT?Category.SPEND:Category.INCOME;
            out.add(new NormalizedTxn(p.accountLast4(),p.occurredAt(),p.direction(),p.amount().setScale(2),c,p.merchant(),p.sourceMessageIds()));}
        return List.copyOf(out);
    }
    private static CanonicalTxn canonicalise(List<ParsedTxn> evidence){
        ParsedTxn r=representative(evidence);
        BigDecimal bal=evidence.stream().map(ParsedTxn::statedBalance).filter(Objects::nonNull).findFirst().orElse(null);
        List<String> ids=evidence.stream().map(ParsedTxn::sourceMessageId).distinct().sorted().toList();
        return new CanonicalTxn(r.accountLast4(),r.occurredAt(),r.direction(),r.amount().setScale(2),r.merchant(),bal,ids);
    }
    private static ParsedTxn representative(List<ParsedTxn> evidence){return evidence.stream().min(Comparator.comparing(ParsedTxn::sourceMessageId)).orElseThrow();}
    private static boolean isUpi(String merchant){return merchant!=null&&merchant.trim().toUpperCase(Locale.ROOT).startsWith("UPI");}
    private static Set<Integer> findTransfers(List<CanonicalTxn> txns){
        Set<Integer> result=new HashSet<>();
        for(int i=0;i<txns.size();i++){CanonicalTxn a=txns.get(i); if(a.direction()!=Direction.DEBIT||a.accountLast4().equals("3310"))continue;
            for(int j=i+1;j<txns.size();j++){CanonicalTxn b=txns.get(j); if(b.occurredAt().isAfter(a.occurredAt().plusMinutes(10)))break;
                if(b.direction()==Direction.CREDIT&&!a.accountLast4().equals(b.accountLast4())&&a.amount().compareTo(b.amount())==0
                    &&sameMerchant(a.merchant(),b.merchant())&&looksLikeOwnTransfer(a.merchant())){result.add(i);result.add(j);break;}}}
        return result;
    }
    private static boolean looksLikeOwnTransfer(String merchant){String m=merchant==null?"":merchant.toUpperCase(Locale.ROOT);return m.startsWith("IMPS/P2A/")||m.contains("NEFT")||m.contains("TRANSFER");}
    private static boolean sameMerchant(String a,String b){return canonicalMerchant(a).equals(canonicalMerchant(b));}
    private static String canonicalMerchant(String merchant){return merchant==null?"":merchant.trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
    private static final class CanonicalTxn{
        private final String accountLast4; private final OffsetDateTime occurredAt; private final Direction direction; private final BigDecimal amount;
        private final String merchant; private final BigDecimal statedBalance; private final List<String> sourceMessageIds;
        CanonicalTxn(String a,OffsetDateTime o,Direction d,BigDecimal am,String m,BigDecimal b,List<String> ids){accountLast4=a;occurredAt=o;direction=d;amount=am;merchant=m;statedBalance=b;sourceMessageIds=ids;}
        String accountLast4(){return accountLast4;} OffsetDateTime occurredAt(){return occurredAt;} Direction direction(){return direction;} BigDecimal amount(){return amount;}
        String merchant(){return merchant;} BigDecimal statedBalance(){return statedBalance;} List<String> sourceMessageIds(){return sourceMessageIds;} String sourceMessageId(){return sourceMessageIds.get(0);}
    }
    private record TransactionKey(String accountLast4,OffsetDateTime occurredAt,Direction direction,BigDecimal amount,String merchant){
        static TransactionKey of(ParsedTxn p){return new TransactionKey(p.accountLast4(),p.occurredAt(),p.direction(),p.amount().setScale(2),canonicalMerchant(p.merchant()));}
        static TransactionKey of(NormalizedTxn t){return new TransactionKey(t.accountLast4(),t.occurredAt(),t.direction(),t.amount().setScale(2),canonicalMerchant(t.merchant()));}
    }
    public record Stats(int messagesRead,int transactionsWritten,int messagesSkipped,int duplicateEvidence){}
}