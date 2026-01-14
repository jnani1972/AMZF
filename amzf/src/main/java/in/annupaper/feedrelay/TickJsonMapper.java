package in.annupaper.feedrelay;

import in.annupaper.broker.BrokerAdapter.Tick;
import org.json.JSONObject;

import java.math.BigDecimal;

public final class TickJsonMapper {
    private TickJsonMapper() {}

    public static String toJson(Tick t) {
        JSONObject o = new JSONObject();

        o.put("symbol", t.symbol());
        o.put("timestamp", t.timestamp());

        putDecimal(o, "lastPrice", t.lastPrice());
        putDecimal(o, "open", t.open());
        putDecimal(o, "high", t.high());
        putDecimal(o, "low", t.low());
        putDecimal(o, "close", t.close());

        o.put("volume", t.volume());

        putDecimal(o, "bid", t.bid());
        putDecimal(o, "ask", t.ask());
        o.put("bidQty", t.bidQty());
        o.put("askQty", t.askQty());

        return o.toString();
    }

    private static void putDecimal(JSONObject o, String key, BigDecimal v) {
        if (v == null) return;
        o.put(key, v.toPlainString());
    }
}
