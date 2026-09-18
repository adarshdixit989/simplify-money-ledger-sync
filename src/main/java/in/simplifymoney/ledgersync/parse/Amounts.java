package in.simplifymoney.ledgersync.parse;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Amounts {
    private Amounts() {}
    private static final String NUMBER = "[0-9][0-9,]*(?:\\.[0-9]{1,2})?";
    private static final Pattern AMOUNT = Pattern.compile("(?:Rs\\.?|INR)\\s*(" + NUMBER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern BALANCE = Pattern.compile("(?:Avl\\s*Bal|Available\\s*Balance|BalAvl|Avl\\s*Limit)\\s*:?\\s*(?:Rs\\.?|INR)\\s*(" + NUMBER + ")", Pattern.CASE_INSENSITIVE);
    public static BigDecimal first(String body) { Matcher m=AMOUNT.matcher(body); if(!m.find()) return null; return toDecimal(m.group(1)); }
    public static BigDecimal statedBalance(String body) { Matcher m=BALANCE.matcher(body); if(!m.find()) return null; return toDecimal(m.group(1)); }
    private static BigDecimal toDecimal(String raw) { return new BigDecimal(raw.replace(",","")).setScale(2); }
}