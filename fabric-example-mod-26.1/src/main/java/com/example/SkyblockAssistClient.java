package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SkyblockAssistClient implements ClientModInitializer {

    public static SkyblockAssistConfig config;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void onInitializeClient() {
        System.out.println("[SkyblockAI] Mod is loading...");
        loadConfig();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("assist")
                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                            .executes(context -> {
                                String userText = StringArgumentType.getString(context, "query");

                                context.getSource().sendFeedback(Text.literal("§a[AI] Thinking about: §f" + userText));

                                java.util.concurrent.CompletableFuture.runAsync(() -> {
                                    // Call Gemini and get the final string to display
                                    String finalAnswer = askGemini(userText);
                                    context.getSource().sendFeedback(Text.literal("§0§kL§bAPI Results§0§kL§f: " + finalAnswer));
                                });

                                return 1;
                            })
                    )
            );
        });
    }

    private String askGemini(String userText) {
        if (config.geminiApiKey.equals("YOUR_API_KEY_HERE") || config.geminiApiKey.isEmpty()) {
            return "Please set your Gemini API key in the config file!";
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + config.geminiApiKey;

            // JSON required by the Gemini API
            JsonObject body = new JsonObject();

            // 1. Add System Instruction
            JsonObject sysInst = new JsonObject();
            JsonObject sysParts = new JsonObject();
            sysParts.addProperty("text", config.systemPrompt);
            JsonArray sysPartsArr = new JsonArray();
            sysPartsArr.add(sysParts);
            sysInst.add("parts", sysPartsArr);
            body.add("system_instruction", sysInst);

            // 2. Add User Input (Contents)
            JsonObject contents = new JsonObject();
            JsonObject parts = new JsonObject();
            parts.addProperty("text", userText);
            JsonArray partsArr = new JsonArray();
            partsArr.add(parts);
            contents.add("parts", partsArr);
            JsonArray contentsArr = new JsonArray();
            contentsArr.add(contents);
            body.add("contents", contentsArr);

            // 3. Force JSON output format
            JsonObject genConfig = new JsonObject();
            genConfig.addProperty("response_mime_type", "application/json");
            body.add("generationConfig", genConfig);

            // Create and send the request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Dig into the Gemini API response structure to get the generated text
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                String geminiOutput = jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();

                // Parse Gemini's JSON response based on our system prompt
                JsonObject actionJson = JsonParser.parseString(geminiOutput).getAsJsonObject();
                String type = actionJson.get("type").getAsString();

                // Route the logic based on what Gemini decided
                if (type.equals("price")) {
                    String source = actionJson.get("source").getAsString();
                    String itemId = actionJson.get("itemId").getAsString();
                    String price;

                    System.out.println(source);
                    System.out.println(source.getClass());

                    if (source.equals("bz")) {
                        price = fetchCoflnetBazaarPrice(itemId);
                    } else {
                        price = fetchCoflnetAhPrice(itemId);
                    }

                    // Format the output
                    return itemId.replace("_", " ") + " price is " + price;
                } else {
                    // It's a general question, return Gemini's text directly
                    return actionJson.get("text").getAsString();
                }

            } else {
                return "Gemini API Error: " + response.statusCode() + " - " + response.body();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error contacting Gemini. Check console for details.";
        }
    }

    private void loadConfig() {
        try {
            File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "skyblock_assist.json");

            if (configFile.exists()) {
                String json = Files.readString(configFile.toPath());
                config = GSON.fromJson(json, SkyblockAssistConfig.class);
            } else {
                config = new SkyblockAssistConfig();
                Files.writeString(configFile.toPath(), GSON.toJson(config));
            }
        } catch (Exception e) {
            e.printStackTrace();
            config = new SkyblockAssistConfig();
        }
    }

    private String fetchCoflnetAhPrice(String itemId) {
        try {
            String url = "https://sky.coflnet.com/api/item/price/" + itemId + "/bin";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SkyblockAssistMod/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                double lowestBin = json.get("lowest").getAsDouble();
                return String.format("%,.0f coins", lowestBin);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown AH Price";
    }

    private String fetchCoflnetBazaarPrice(String itemId) {
        try {
            String url = "https://sky.coflnet.com/api/bazaar/" + itemId + "/snapshot";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SkyblockAssistMod/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                double sellPrice = json.get("sellPrice").getAsDouble();
                return String.format("%,.0f coins", sellPrice);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown BZ Price";
    }
}