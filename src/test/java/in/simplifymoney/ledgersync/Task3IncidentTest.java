package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import in.simplifymoney.ledgersync.parse.Amounts;
import in.simplifymoney.ledgersync.parse.HdfcSmsParser;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Task3IncidentTest {
 @Test void productionWaterCanAlertUsesTransactionAmountNotAvailableBalance(){
  String body="Rs.5 debited from a/c **4821 on 04-07-26 at 11:54 to UPI/WATER CAN. Avl Bal: Rs.92,213.10. Not you? Call 18002586161";
  assertEquals(new BigDecimal("5.00"),Amounts.first(body)); assertEquals(new BigDecimal("92213.10"),Amounts.statedBalance(body));
 }
 @Test void hdfcEMandateDateIsParsedAsTheBankTransactionTime(){
  String body="E-mandate! Rs.649.00 will be deducted from your HDFC Bank A/c XX4821 on 22-07-26 at 06:15 for NETFLIX ENTERTAINMENT. Avl Bal: Rs.46,868.04";
  var raw=new in.simplifymoney.ledgersync.model.RawMessage("m-test","sms",HdfcSmsParser.SENDER,java.time.OffsetDateTime.parse("2026-07-22T06:15:00+05:30"),"dev",body);
  var parsed=new HdfcSmsParser().parse(raw).orElseThrow();
  assertEquals(new BigDecimal("649.00"),parsed.amount()); assertEquals("2026-07-22T06:15+05:30",parsed.occurredAt().toString());
 }
 @Test void wholeRupeeTransactionAmountWinsOverLaterDecimalBalance(){
  String body="Dear Customer, Acct XX9075 is debited with Rs.25 on 04/07/2026 07:54. Info: UPI/STATIONERY. Avl Bal Rs.49,857.25 -ICICI Bank";
  assertEquals(new BigDecimal("25.00"),Amounts.first(body)); assertEquals(new BigDecimal("49857.25"),Amounts.statedBalance(body));
 }
}