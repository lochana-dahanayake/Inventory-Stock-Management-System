package com.ims185.servlet;

import com.ims185.config.FilePaths;
import com.ims185.util.ActivityLogger;
import com.ims185.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/settings")
public class SettingsServlet extends HttpServlet {
    private Map<String, String> loadSettingsFromFile() {
        Map<String, String> settings = new HashMap<>();
        
        // Try multiple locations for the settings file
        Path[] possiblePaths = new Path[] {
            // Primary path from FilePaths config
            Paths.get(FilePaths.getDataDirectory(), "settings.txt"),
            // Alt path - WEB-INF/data
            Paths.get(getServletContext().getRealPath("/WEB-INF/data"), "settings.txt"),
            // Target directory path
            Paths.get(getServletContext().getRealPath("/"), "WEB-INF", "data", "settings.txt"),
            // Direct target path for debugging
            Paths.get("c:/Users/WTF/Desktop/IMS-184/target/IMS-185/WEB-INF/data", "settings.txt")
        };
        
        boolean fileFound = false;
        for (Path settingsPath : possiblePaths) {
            File settingsFile = settingsPath.toFile();
            System.out.println("Checking settings file at: " + settingsPath + " exists: " + settingsFile.exists());
            
            if (settingsFile.exists()) {
                fileFound = true;
                try (BufferedReader reader = new BufferedReader(new FileReader(settingsPath.toString()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            settings.put(parts[0], parts[1]);
                        }
                    }
                    System.out.println("Successfully loaded settings from: " + settingsPath);
                    break;
                } catch (IOException e) {
                    System.err.println("Error reading settings from " + settingsPath + ": " + e.getMessage());
                }
            }
        }

        // If no settings file found, create with defaults
        if (!fileFound || !settings.containsKey("threshold")) {
            settings.put("threshold", "10");
            saveSettingsToFile(settings);
        }
        
        return settings;
    }

    private void saveSettingsToFile(Map<String, String> settings) {
        // Try to save to multiple locations to ensure it's available
        Path[] savePaths = new Path[] {
            // Primary path from FilePaths config
            Paths.get(FilePaths.getDataDirectory(), "settings.txt"),
            // Alt path - WEB-INF/data
            Paths.get(getServletContext().getRealPath("/WEB-INF/data"), "settings.txt"),
            // Target directory path
            Paths.get(getServletContext().getRealPath("/"), "WEB-INF", "data", "settings.txt"),
            // Direct target path for debugging
            Paths.get("c:/Users/WTF/Desktop/IMS-184/target/IMS-185/WEB-INF/data", "settings.txt")
        };
        
        for (Path settingsPath : savePaths) {
            try {
                File settingsFile = settingsPath.toFile();
                
                // Create directories if they don't exist
                if (!settingsFile.getParentFile().exists()) {
                    settingsFile.getParentFile().mkdirs();
                }
                
                // Write settings to file
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsPath.toString()))) {
                    for (Map.Entry<String, String> entry : settings.entrySet()) {
                        writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                    }
                }
                
                System.out.println("Settings saved successfully to: " + settingsPath);
            } catch (IOException e) {
                System.err.println("Error saving settings to " + settingsPath + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        if (session.getAttribute("loggedInUser") == null || !((User) session.getAttribute("loggedInUser")).getRole().equals("admin")) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        
        // Debug logging
        System.out.println("SettingsServlet doGet - Context Path: " + request.getContextPath());
        String realPath = getServletContext().getRealPath("/");
        System.out.println("Real Path: " + realPath);
        System.out.println("FilePaths.getDataDirectory() = " + FilePaths.getDataDirectory());
        
        // Load settings from file
        Map<String, String> settings = loadSettingsFromFile();
        System.out.println("Loaded settings: " + settings);
        
        String thresholdValue = settings.getOrDefault("threshold", "10");
        System.out.println("Using threshold value: " + thresholdValue);
        
        // Set as request attribute to be used in the JSP
        request.setAttribute("threshold", thresholdValue);
        
        request.getRequestDispatcher("/settings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        if (session.getAttribute("loggedInUser") == null || !((User) session.getAttribute("loggedInUser")).getRole().equals("admin")) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        
        Map<String, String> settings = loadSettingsFromFile();
        String threshold = request.getParameter("threshold");
        
        // Validate the threshold value
        if (threshold != null) {
            try {
                int thresholdValue = Integer.parseInt(threshold);
                
                // Ensure minimum threshold is 1
                if (thresholdValue < 1) {
                    // Set error message and forward back to the settings page
                    request.setAttribute("error", "Low Stock Threshold must be at least 1");
                    request.setAttribute("threshold", settings.getOrDefault("threshold", "10"));
                    request.getRequestDispatcher("/settings.jsp").forward(request, response);
                    return;
                }
                
                // Valid threshold value, save it
                settings.put("threshold", threshold);
                ActivityLogger.logUserActivity(request, "updated", "inventory threshold setting to " + threshold);
                saveSettingsToFile(settings);
                
                // Redirect with success message
                response.sendRedirect(request.getContextPath() + "/settings?success=true");
                return;
            } catch (NumberFormatException e) {
                // Not a valid number
                request.setAttribute("error", "Low Stock Threshold must be a valid number");
                request.setAttribute("threshold", settings.getOrDefault("threshold", "10"));
                request.getRequestDispatcher("/settings.jsp").forward(request, response);
                return;
            }
        }
        
        // If we get here, just redirect back to settings
        response.sendRedirect(request.getContextPath() + "/settings");
    }
}