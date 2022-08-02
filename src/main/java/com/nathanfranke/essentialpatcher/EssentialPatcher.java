package com.nathanfranke.essentialpatcher;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

class Proxy {
    enum Direction {
        INCOMING,
        OUTGOING,
    }

    private final Path outfitsPath = FabricLoader.getInstance().getConfigDir().resolve("essential_patched_outfits.json");
    private final JsonArray outfits = loadOutfits(outfitsPath);

    private static JsonArray loadOutfits(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonArray();
        } catch (IOException ignored) {
        }
        return new JsonArray();
    }

    private final WebSocketClient client = new WebSocketClient(URI.create("wss://connect.essential.gg/v1")) {
        private final Map<Integer, String> packetTypes = new HashMap<>();
        private final Map<String, Predicate<JsonElement>> mappers = new HashMap<>();

        private final Set<String> knownCosmetics = new HashSet<>();

        {
            setTcpNoDelay(false);

            // INCOMING
            mappers.put("connection.ConnectionKeepAlivePacket", null);
            mappers.put("response.ResponseActionPacket", null);
            mappers.put("cosmetic.categories.ServerCosmeticCategoriesPopulatePacket", null);
            mappers.put("cosmetic.ServerCosmeticTypesPopulatePacket", null);
            mappers.put("cosmetic.ServerCosmeticsUserEquippedVisibilityPacket", null);
            mappers.put("cosmetic.outfit.ServerCosmeticOutfitPopulatePacket", json -> {
                JsonArray serverData = json.getAsJsonObject().get("outfits").getAsJsonArray();
                serverData.forEach(e -> {
                    String id = e.getAsJsonObject().get("a").getAsString();
                    boolean has = false;
                    for (JsonElement e2 : outfits) {
                        if (e2.getAsJsonObject().get("a").getAsString().equals(id)) {
                            has = true;
                            break;
                        }
                    }
                    if (!has) {
                        outfits.add(e);
                    }
                });

                json.getAsJsonObject().add("outfits", outfits);
                return true;
            });
            mappers.put("cosmetic.ServerCosmeticsPopulatePacket", json -> {
                JsonArray arr = json.getAsJsonObject().get("a").getAsJsonArray();
                arr.forEach(element -> {
                    String id = element.getAsJsonObject().get("a").getAsString();
                    knownCosmetics.add(id);
                });
                return true;
            });
            mappers.put("cosmetic.ServerCosmeticsUserUnlockedPacket", json -> {
                JsonArray arr = json.getAsJsonObject().get("a").getAsJsonArray();
                while (arr.size() > 0) {
                    arr.remove(0);
                }
                for (String s : knownCosmetics) {
                    arr.add(s);
                }
                return true;
            });
            mappers.put("profile.ServerProfileStatusPacket", null);
            mappers.put("profile.ServerProfileActivityPacket", null);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            EssentialPatcher.LOGGER.info("Connected to remote server.");
        }

        @Override
        public void onMessage(String message) {
            throw new RuntimeException("Expected binary message.");
        }

        @Override
        public void onMessage(ByteBuffer message) {
            handlePacket(Direction.INCOMING, message, packetTypes, mappers, server::broadcast);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            EssentialPatcher.LOGGER.error("Connection to remote server closed (code {}, reason {}).", code, reason);
        }

        @Override
        public void onError(Exception ex) {
            ex.printStackTrace();
        }
    };

    private final WebSocketServer server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 13372)) {
        private final Map<Integer, String> packetTypes = new HashMap<>();
        private final Map<String, Predicate<JsonElement>> mappers = new HashMap<>();

        {
            setReuseAddr(true);

            // OUTGOING
            mappers.put("connection.ConnectionKeepAlivePacket", null);
            mappers.put("response.ResponseActionPacket", null);
            mappers.put("connection.ClientConnectionLoginPacket", null);
            mappers.put("connection.ClientConnectionDisconnectPacket", null);
            mappers.put("cosmetic.capes.ClientCosmeticCapesUnlockedPacket", null);
            mappers.put("cosmetic.categories.ClientCosmeticCategoriesRequestPacket", null);
            mappers.put("cosmetic.ClientCosmeticRequestPacket", null);
            mappers.put("cosmetic.outfit.ClientCosmeticOutfitEquippedCosmeticsUpdatePacket", json -> {
                String outfit = json.getAsJsonObject().get("a").getAsString();
                String slot = json.getAsJsonObject().get("b").getAsString();
                String item = json.getAsJsonObject().has("c") ? json.getAsJsonObject().get("c").getAsString() : null;

                for (JsonElement e : outfits) {
                    if (e.getAsJsonObject().get("a").getAsString().equals(outfit)) {
                        JsonObject obj = e.getAsJsonObject().get("d").getAsJsonObject();
                        if (obj.has(slot)) {
                            obj.remove(slot);
                        }
                        if (item != null) {
                            obj.add(slot, new JsonPrimitive(item));
                        }
                        break;
                    }
                }

                try {
                    Files.writeString(outfitsPath, outfits.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Send cape only.
                return slot.equals("CAPE");
            });
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            try {
                client.connectBlocking();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            client.close();
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            throw new RuntimeException("Expected binary message.");
        }

        @Override
        public void onMessage(WebSocket conn, ByteBuffer message) {
            handlePacket(Direction.OUTGOING, message, packetTypes, mappers, client::send);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            ex.printStackTrace();
        }

        @Override
        public void onStart() {
            EssentialPatcher.LOGGER.info("Fake Essential server is starting...");
        }
    };

    void handlePacket(Direction dir, ByteBuffer message, Map<Integer, String> packetTypes, Map<String, Predicate<JsonElement>> mappers, Consumer<ByteBuffer> pass) {
        try {
            ByteArrayInputStream raw = new ByteArrayInputStream(message.array());
            DataInputStream data = new DataInputStream(raw);

            int typeId = data.readInt();
            String name = packetTypes.get(typeId);

            String uuidRaw; {
                int length = data.readInt();
                byte[] buffer = new byte[length];
                int read = data.read(buffer);
                assert read == length;
                uuidRaw = new String(buffer, StandardCharsets.UTF_8);
            }

            String jsonRaw; {
                int length = data.readInt();
                byte[] buffer = new byte[length];
                int read = data.read(buffer);
                assert read == length;
                jsonRaw = new String(buffer, StandardCharsets.UTF_8);
            }

            JsonElement json = JsonParser.parseString(jsonRaw);

            String relatedName = name;
            if (typeId == 0) {
                String n = json.getAsJsonObject().get("a").getAsString();
                int i = json.getAsJsonObject().get("b").getAsInt();
                packetTypes.put(i, n);
                relatedName = n;
            }

            if (!mappers.containsKey(relatedName)) {
                if (typeId != 0) {
                    EssentialPatcher.LOGGER.info("Blocking {} packet '{}'.", dir, name);
                }
                return;
            }

            Predicate<JsonElement> mapper = mappers.get(relatedName);

            if (typeId == 0 || mapper == null) {
                // Send type or basic packets ad-hoc.
                pass.accept(message);
                return;
            }

            boolean result = mapper.test(json);
            if (!result) {
                EssentialPatcher.LOGGER.info("Cancelling {} packet '{}'.", dir, name);
                return;
            }

            String newJson = json.toString();

            ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(outRaw);
            out.writeInt(typeId);
            out.writeInt(uuidRaw.length());
            out.write(uuidRaw.getBytes(StandardCharsets.UTF_8));
            out.writeInt(newJson.length());
            out.write(newJson.getBytes(StandardCharsets.UTF_8));

            pass.accept(ByteBuffer.wrap(outRaw.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() throws InterruptedException {
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public int getPort() {
        return server.getPort();
    }
}

public class EssentialPatcher implements PreLaunchEntrypoint {
    public static final Logger LOGGER = LoggerFactory.getLogger("essentialpatcher");

    @Override
    public void onPreLaunch() {
        try {
            Proxy proxy = new Proxy();
            proxy.start();

            System.setProperty("essential.cm.host", "ws://localhost:" + proxy.getPort());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
