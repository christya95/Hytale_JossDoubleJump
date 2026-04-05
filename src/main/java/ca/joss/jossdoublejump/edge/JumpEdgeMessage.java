package ca.joss.jossdoublejump.edge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import javax.annotation.Nullable;

/** Client-originated jump transition (DOWN / UP only). */
public final class JumpEdgeMessage {

    public static final String PREFIX = "jdj:v1:";

    private static final Gson GSON = new Gson();

    public final int v;
    public final long seq;
    /** {@code "DOWN"} or {@code "UP"} */
    public final String t;

    public JumpEdgeMessage(int v, long seq, String t) {
        this.v = v;
        this.seq = seq;
        this.t = t;
    }

    /**
     * Parse JSON body after {@link #PREFIX}. Expected: {@code {"v":1,"seq":1,"t":"DOWN"}}.
     *
     * @return null if malformed or wrong {@code t}
     */
    @Nullable
    public static JumpEdgeMessage tryParseJsonBody(String jsonBody) {
        if (jsonBody == null || jsonBody.isEmpty()) {
            return null;
        }
        try {
            JsonObject o = GSON.fromJson(jsonBody, JsonObject.class);
            if (o == null || !o.has("v") || !o.has("seq") || !o.has("t")) {
                return null;
            }
            int v = o.get("v").getAsInt();
            long seq = o.get("seq").getAsLong();
            String t = o.get("t").getAsString();
            if (!"DOWN".equals(t) && !"UP".equals(t)) {
                return null;
            }
            return new JumpEdgeMessage(v, seq, t);
        } catch (JsonParseException | ClassCastException | IllegalStateException | NumberFormatException e) {
            return null;
        }
    }

    /** Full wire string including prefix (for tests / client). */
    public String toWirePayload() {
        return PREFIX + GSON.toJson(new Payload(v, seq, t));
    }

    private record Payload(int v, long seq, String t) {}
}
