package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailParser implements MessageParser {
    private static final Pattern ALERT=Pattern.compile("Your account ending (?<acct>\\d{4}) has been (?<dir>debited|credited)\\s+with\\s+(?:INR|Rs\\.?)\\s*(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?)\\.\\s*Merchant / Remarks:\\s*(?<merchant>[^\\r\\n]+)",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern HEADER_DATE=Pattern.compile("^Date:\\s*\\w{3},\\s*(?<when>\\d{2} \\w{3} \\d{4} \\d{2}:\\d{2}:\\d{2} [+-]\\d{4})",Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
    private static final DateTimeFormatter EMAIL_DATE=DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss xx",Locale.ENGLISH);
    public boolean supports(RawMessage m){return "email".equalsIgnoreCase(m.channel())&&("alerts@hdfcbank.net".equalsIgnoreCase(m.sender())||"alerts@icicibank.com".equalsIgnoreCase(m.sender()));}
    public Optional<ParsedTxn> parse(RawMessage m){
        Matcher alert=ALERT.matcher(m.body()),header=HEADER_DATE.matcher(m.body()); if(!alert.find()||!header.find())return Optional.empty();
        OffsetDateTime occurredAt; try{occurredAt=OffsetDateTime.parse(header.group("when"),EMAIL_DATE).withOffsetSameInstant(Dates.IST);}catch(DateTimeParseException e){return Optional.empty();}
        BigDecimal amount; try{amount=new BigDecimal(alert.group("amount").replace(",","")).setScale(2);}catch(NumberFormatException e){return Optional.empty();}
        Direction direction="debited".equalsIgnoreCase(alert.group("dir"))?Direction.DEBIT:Direction.CREDIT;
        return Optional.of(new ParsedTxn(alert.group("acct"),occurredAt,direction,amount,alert.group("merchant").trim(),null,m.messageId()));
    }
}