package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;// <-- Change: ClientCommands instead of ClientCommandManager
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
import java.util.concurrent.CompletableFuture;

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
                    // 1. Define the argument name and type ("query" is just a label)
                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                            .executes(context -> {
                                // 2. Extract the string from the context
                                String userText = StringArgumentType.getString(context, "query");

                                context.getSource().sendFeedback(Text.literal("§a[AI] Searching for: §f" + userText));

                                java.util.concurrent.CompletableFuture.runAsync(() -> {
                                    // You can now use userText here to search specifically for what the user typed!
                                    String price = fetchCoflnetBazaarPrice(userText.toUpperCase().replace(" ", "_"));
                                    context.getSource().sendFeedback(Text.literal("§0§kL§bAPI Results§0§kL§f: " + userText + " is " + price));
                                });

                                return 1;
                            })
                    )
            );
        });
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