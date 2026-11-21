import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.Json;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoCodec;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoException;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoJson;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoReader;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoSchema;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoValues;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoWriter;

/**
 * JVM test for the client's hand-written Apollo protobuf layer against
 * REAL serialized messages: tests/apollo/fixtures.txt holds Any-wrapped
 * lunar:apollo payloads produced by an independent encoder (protobufjs,
 * scripts/gen_apollo_fixtures.cjs) plus that encoder's own JSON.
 *
 *  1. WIRE: varint/zigzag/fixed/float edge values round-trip.
 *  2. DECODE: every fixture unpacks from its Any envelope and its fields
 *     read back exactly (negative sint32, int32 colors, doubles, nested
 *     Durations, packed enums, fixed64 UUIDs, map<string, Value>).
 *  3. RE-ENCODE: decode then encode reproduces the reference bytes
 *     byte-for-byte.
 *  4. JSON: the reference encoder's camelCase JSON and Apollo-example
 *     snake_case JSON ("@type", "1.5s" durations, numeric enums) decode
 *     to the same bytes; our JSON output parses back identically.
 *  5. SAFETY: unknown fields / wrong wire types are skipped, unknown Any
 *     types yield an empty message, truncated / hostile input only ever
 *     throws ProtoException (fuzzed), nesting is capped.
 *
 * Exit 0 = holds; 1 = violation.
 */
public class ApolloProtoTest {

        static int failures = 0;
        static int checks = 0;

        static void check(boolean ok, String what) {
                checks++;
                if (!ok) {
                        System.out.println("FAIL: " + what);
                        failures++;
                }
        }

        static byte[] hex(String h) {
                byte[] b = new byte[h.length() / 2];
                for (int i = 0; i < b.length; i++) {
                        b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
                }
                return b;
        }

        static String toHex(byte[] b) {
                StringBuilder sb = new StringBuilder();
                for (byte x : b) {
                        sb.append(String.format("%02x", x & 0xFF));
                }
                return sb.toString();
        }

        public static void main(String[] args) throws Exception {
                String fixturePath = args.length > 0 ? args[0] : "tests/apollo/fixtures.txt";
                List<String[]> fx = new ArrayList<>();
                for (String line : Files.readAllLines(Paths.get(fixturePath), StandardCharsets.UTF_8)) {
                        if (!line.isEmpty()) {
                                fx.add(line.split("\t"));
                        }
                }
                check(fx.size() >= 12, "fixtures loaded: " + fx.size());
                check(ProtoSchema.messageCount() >= 124 && ProtoSchema.enumCount() >= 13,
                                "schema tables loaded: " + ProtoSchema.messageCount() + "/" + ProtoSchema.enumCount());

                wire();

                for (String[] f : fx) {
                        String name = f[0];
                        String type = f[1];
                        byte[] any = hex(f[2]);
                        ProtoMessage m = ProtoCodec.decodeAny(any);
                        check(type.equals(m.type()), name + ": Any unpacks to " + m.type());
                        check(m.unknownFieldCount() == 0, name + ": no unknown fields");
                        byte[] again = ProtoCodec.encodeAny(m);
                        check(Arrays.equals(any, again), name + ": re-encode is byte-identical\n  want "
                                        + f[2] + "\n  got  " + toHex(again));
                        // protobufjs's toObject() does not apply the proto3 JSON
                        // mapping to google.protobuf.Value (it prints {"boolValue":true}
                        // instead of true), so its JSON is only a reference for
                        // messages without map<string, Value> fields
                        if (!f[3].contains("Value\":")) {
                                ProtoMessage fromJson = ProtoJson.fromJson(type, Json.parse(f[3]));
                                check(Arrays.equals(any, ProtoCodec.encodeAny(fromJson)),
                                                name + ": reference camelCase JSON decodes to the same bytes: " + fromJson);
                        }
                        String ours = ProtoJson.toJson(m);
                        ProtoMessage back = ProtoJson.parse(ours);
                        check(Arrays.equals(any, ProtoCodec.encodeAny(back)), name + ": our JSON round-trips: " + ours);
                        fields(name, m);
                }

                snakeCaseJson();
                safety(fx);

                System.out.println("ApolloProtoTest: " + checks + " checks, failures=" + failures);
                System.exit(failures > 0 ? 1 : 0);
        }

        static void wire() {
                long[] longs = { 0, 1, -1, 127, 128, 300, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE };
                for (long v : longs) {
                        ProtoWriter w = new ProtoWriter();
                        w.writeVarint64(v).writeSint64(v).writeFixed64(v).writeDouble(v * 0.5);
                        ProtoReader r = new ProtoReader(w.toByteArray());
                        check(r.readVarint64() == v && r.readSint64() == v && r.readFixed64() == v
                                        && r.readDouble() == v * 0.5 && !r.hasMore(), "64-bit wire round trip " + v);
                        int iv = (int) v;
                        w = new ProtoWriter();
                        w.writeInt32(iv).writeSint32(iv).writeFixed32(iv).writeFloat(iv * 0.25f);
                        r = new ProtoReader(w.toByteArray());
                        check(r.readVarint32() == iv && r.readSint32() == iv && r.readFixed32() == iv
                                        && r.readFloat() == iv * 0.25f, "32-bit wire round trip " + iv);
                }
                ProtoWriter w = new ProtoWriter();
                w.writeInt32(-1);
                check(w.size() == 10, "negative int32 is a 10-byte varint (protobuf rule)");
                w = new ProtoWriter();
                w.writeSint32(-1);
                check(Arrays.equals(w.toByteArray(), new byte[] { 1 }), "zigzag(-1) = 1");
        }

        static void fields(String name, ProtoMessage m) {
                switch (name) {
                case "notification":
                        check(m.getDurationMillis("displayTime") == 5000, "notification display time 5s");
                        check(m.getString("titleAdventureJsonLines").equals("{\"text\":\"Hello\"}"), "notification title");
                        break;
                case "title":
                        check(m.getInt("titleType") == 2 && m.getEnumName("titleType").equals("TITLE_TYPE_SUBTITLE"),
                                        "title type enum: " + m.getEnumName("titleType"));
                        check(m.getFloat("scale") == 1.5f, "title scale float");
                        check(m.getDurationMillis("fadeInTime") == 500 && m.getDurationMillis("fadeOutTime") == 1250,
                                        "title durations with nanos");
                        break;
                case "waypoint": {
                        ProtoMessage loc = m.getMessage("location");
                        check(loc != null && loc.getInt("x") == -100 && loc.getInt("y") == 64
                                        && loc.getInt("z") == Integer.MAX_VALUE, "waypoint sint32 block location " + loc);
                        check(m.getMessage("color").getInt("color") == -32768, "waypoint int32 color");
                        check(m.getBool("preventRemoval") && m.has("hidden") && !m.getBool("hidden"), "waypoint bools");
                        break;
                }
                case "cooldown":
                        check(m.getDurationMillis("duration") == 15000, "cooldown 15s");
                        check("ENDER_PEARL".equals(m.getMessage("icon").getMessage("itemStack").getString("itemName")),
                                        "cooldown nested oneof icon");
                        break;
                case "configurable": {
                        List<ProtoMessage> s = m.getMessages("configurableSettings");
                        check(s.size() == 3, "3 configurable settings");
                        Map<String, Object> props = s.get(0).getMap("properties");
                        check(Boolean.TRUE.equals(ProtoValues.toJava((ProtoMessage) props.get("competitive-game")))
                                        && Double.valueOf(50).equals(ProtoValues.toJava((ProtoMessage) props.get("brightness")))
                                        && Arrays.asList("/server", "/hub").equals(
                                                        ProtoValues.toJava((ProtoMessage) props.get("competitive-commands"))),
                                        "server_rule properties map<string, Value>: " + props);
                        check(Boolean.FALSE.equals(ProtoValues.toJava((ProtoMessage) s.get(1).getMap("properties").get("zoom.enabled"))),
                                        "mod_setting zoom.enabled=false");
                        check("waypoint".equals(s.get(2).getString("apolloModule")) && !s.get(2).getBool("enable"),
                                        "module disable entry");
                        break;
                }
                case "border": {
                        ProtoMessage b = m.getMessage("bounds");
                        check(b.getDouble("minX") == -50.5 && b.getDouble("maxZ") == 50.25 && m.getInt("durationTicks") == 200,
                                        "border doubles + ticks");
                        break;
                }
                case "team": {
                        ProtoMessage u = m.getMessages("members").get(0).getMessage("playerUuid");
                        check(u.getLong("high64") == -6917529027641081856L && u.getLong("low64") == 1311768467294899695L,
                                        "fixed64 uuid halves: " + u);
                        break;
                }
                case "limb":
                        check(m.getList("bodyParts").equals(Arrays.asList(1, 2, 3)), "packed repeated enum: " + m.getList("bodyParts"));
                        break;
                case "hologram":
                        check(m.getStrings("adventureJsonLines").size() == 2, "repeated strings");
                        break;
                case "richpresence":
                        check(m.getInt("teamMaxSize") == 4 && "BedWars".equals(m.getString("gameName")), "rich presence");
                        break;
                case "handshake":
                        check("v26_1".equals(m.getMessage("minecraftVersion").getString("enum")), "handshake version string");
                        break;
                default:
                        break;
                }
        }

        static void snakeCaseJson() {
                // exactly how Apollo's bukkit JSON example builds a title (JsonPacketUtil style)
                String json = "{\"@type\":\"type.googleapis.com/lunarclient.apollo.title.v1.DisplayTitleMessage\","
                                + "\"title_type\":1,\"adventure_json_message\":\"{\\\"text\\\":\\\"Hi\\\"}\",\"scale\":1.0,"
                                + "\"fade_in_time\":\"1.5s\",\"display_time\":\"0.250s\",\"fade_out_time\":\"0.3s\",\"future_field\":7}";
                ProtoMessage m = ProtoJson.parse(json);
                check(m.getInt("titleType") == 1 && m.getDurationMillis("fadeInTime") == 1500
                                && m.getDurationMillis("displayTime") == 250 && m.getDurationMillis("fadeOutTime") == 300,
                                "snake_case JSON with numeric enum + duration strings: " + m);
                check(m.unknownFieldCount() == 1, "unknown JSON key counted, not fatal");
                String enable = "{\"@type\":\"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage\","
                                + "\"configurable_settings\":[{\"apollo_module\":\"server_rule\",\"enable\":true,\"properties\":"
                                + "{\"competitive-game\":false,\"brightness\":50,\"competitive-commands\":[\"/server\",\"/hub\"],\"x\":null}}]}";
                ProtoMessage c = ProtoJson.parse(enable);
                Map<String, Object> props = c.getMessages("configurableSettings").get(0).getMap("properties");
                check(props.size() == 4 && Boolean.FALSE.equals(ProtoValues.toJava((ProtoMessage) props.get("competitive-game")))
                                && ProtoValues.toJava((ProtoMessage) props.get("x")) == null,
                                "JSON properties become Values: " + props);
                ProtoMessage unknown = ProtoJson.parse("{\"@type\":\"type.googleapis.com/lunarclient.apollo.future.v9.Thing\",\"a\":1}");
                check(unknown.type().endsWith("future.v9.Thing") && unknown.values().isEmpty(), "unknown JSON type is type-only");
                boolean threw = false;
                try {
                        ProtoJson.parse("{\"no_type\":1}");
                } catch (ProtoException e) {
                        threw = true;
                }
                check(threw, "JSON without @type is rejected");
                check(Json.parse("{\"big\":18446744073709551615}") instanceof Map, "huge integers parse");
                ProtoMessage ts = ProtoJson.fromJson("google.protobuf.Timestamp", "2024-05-01T12:30:00.5+02:00");
                check(ts.getLong("seconds") == 1714559400L && ts.getInt("nanos") == 500000000,
                                "RFC 3339 timestamp: " + ts);
        }

        static void safety(List<String[]> fx) {
                byte[] wp = hex(fx.get(2)[2]);
                // unknown field 99 (varint) and a wrong-wire-type field 1 appended to the inner message
                ProtoMessage inner = ProtoCodec.decodeAny(wp);
                byte[] innerBytes = ProtoCodec.encode(inner);
                ProtoWriter w = new ProtoWriter();
                w.writeVarint64(innerBytes.length + 0);
                byte[] extra = new ProtoWriter().writeTag(99, 0).writeVarint64(12345).writeTag(1, 0).writeVarint64(7)
                                .writeTag(50, 5).writeFixed32(1).toByteArray();
                byte[] grown = new byte[innerBytes.length + extra.length];
                System.arraycopy(innerBytes, 0, grown, 0, innerBytes.length);
                System.arraycopy(extra, 0, grown, innerBytes.length, extra.length);
                ProtoMessage tolerant = ProtoCodec.decode(inner.type(), grown);
                check(tolerant.unknownFieldCount() == 3 && "KoTH".equals(tolerant.getString("name")),
                                "unknown + mistyped fields skipped, known fields kept: " + tolerant);

                ProtoMessage futureAny = new ProtoMessage(ProtoCodec.ANY);
                futureAny.set("typeUrl", "type.googleapis.com/lunarclient.apollo.future.v9.Thing");
                futureAny.set("value", new byte[] { 8, 1 });
                ProtoMessage fut = ProtoCodec.decodeAny(ProtoCodec.encode(futureAny));
                check(fut.type().endsWith("Thing") && fut.values().isEmpty(), "unknown Any type yields an empty message");

                int truncThrows = 0;
                for (int cut = 1; cut < wp.length; cut++) {
                        try {
                                ProtoCodec.decodeAny(Arrays.copyOf(wp, cut));
                        } catch (ProtoException e) {
                                truncThrows++;
                        }
                }
                check(truncThrows > wp.length / 2, "truncated payloads are rejected (" + truncThrows + "/" + (wp.length - 1) + ")");

                Random rnd = new Random(26_1_2);
                int otherThrowables = 0;
                for (int i = 0; i < 20000; i++) {
                        byte[] src = hex(fx.get(i % fx.size())[2]);
                        byte[] mut = src.clone();
                        int flips = 1 + rnd.nextInt(4);
                        for (int k = 0; k < flips; k++) {
                                mut[rnd.nextInt(mut.length)] = (byte) rnd.nextInt(256);
                        }
                        if (rnd.nextInt(5) == 0) {
                                mut = Arrays.copyOf(mut, rnd.nextInt(mut.length + 1));
                        }
                        try {
                                ProtoCodec.decodeAny(mut);
                        } catch (ProtoException e) {
                                // expected kind of failure
                        } catch (Throwable t) {
                                otherThrowables++;
                                if (otherThrowables < 4) {
                                        System.out.println("  fuzz threw " + t + " on " + toHex(mut));
                                }
                        }
                }
                check(otherThrowables == 0, "20000 fuzzed payloads only ever throw ProtoException (" + otherThrowables + " others)");
                int jsonOthers = 0;
                String base = fx.get(4)[3];
                for (int i = 0; i < 5000; i++) {
                        char[] c = base.toCharArray();
                        c[rnd.nextInt(c.length)] = "{}[]\",:0a-.e\\".charAt(rnd.nextInt(13));
                        try {
                                ProtoJson.fromJson(fx.get(4)[1], Json.parse(new String(c)));
                        } catch (ProtoException e) {
                                // expected
                        } catch (Throwable t) {
                                jsonOthers++;
                                if (jsonOthers < 4) {
                                        System.out.println("  json fuzz threw " + t + " on " + new String(c));
                                }
                        }
                }
                check(jsonOthers == 0, "5000 fuzzed JSON payloads only ever throw ProtoException (" + jsonOthers + " others)");

                // nesting bomb: Value -> listValue -> values -> Value ... 200 deep
                byte[] bomb = new ProtoWriter().writeTag(4, 0).writeVarint64(1).toByteArray();
                for (int i = 0; i < 200; i++) {
                        byte[] list = new ProtoWriter().writeTag(1, 2).writeBytes(bomb).toByteArray();
                        bomb = new ProtoWriter().writeTag(6, 2).writeBytes(list).toByteArray();
                }
                boolean capped = false;
                try {
                        ProtoCodec.decode("google.protobuf.Value", bomb);
                } catch (ProtoException e) {
                        capped = true;
                }
                check(capped, "nesting bomb is rejected, not a stack overflow");
                StringBuilder deep = new StringBuilder();
                for (int i = 0; i < 500; i++) {
                        deep.append('[');
                }
                capped = false;
                try {
                        Json.parse(deep.toString());
                } catch (ProtoException e) {
                        capped = true;
                }
                check(capped, "deep JSON is rejected");
        }
}
