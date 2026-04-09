package com.example;

public class SkyblockAssistConfig {
    // Put your Gemini API key here
    public String geminiApiKey = "AIzaSyBqA-VL8kjjsoimTIam0ViObCGxFvE-uRo";

    // Instructions for Gemini to output strict JSON
    public String systemPrompt = "You are a Skyblock expert. You must output raw JSON only. " +
            "If the user asks a general question, return: {\"type\": \"answer\", \"text\": \"your short punchy answer\"}. " +
            "If the user asks for the price of an item, determine if it is sold on the Bazaar (bz) or Auction House (ah). " +
            "Return: {\"type\": \"price\", \"source\": \"bz\" or \"ah\", \"itemId\": \"ITEM_ID_IN_CAPS\"}. " +
            "Make sure the itemId matches Hypixel's internal API IDs (e.g., RECOMBOBULATOR_3000, HYPERION).";
}