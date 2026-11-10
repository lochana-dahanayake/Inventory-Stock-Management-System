package com.ims185.servlet;

import com.ims185.config.FilePaths;
import com.ims185.model.Item;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@WebServlet("/stockalerts")
public class StockAlertsServlet extends HttpServlet {
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int CRITICAL_STOCK_THRESHOLD = 5;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        if (session.getAttribute("loggedInUser") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        try {
            List<Item> allItems = loadItemsFromFile();
            
            if (allItems.isEmpty()) {
                request.setAttribute("error", "No items found in the inventory.");
            } else {
                List<Item> lowStockItems = allItems.stream()
                        .filter(item -> item.getStock() <= LOW_STOCK_THRESHOLD)
                        .collect(Collectors.toList());

                // Set attributes for JSP
                request.setAttribute("threshold", LOW_STOCK_THRESHOLD);
                request.setAttribute("criticalThreshold", CRITICAL_STOCK_THRESHOLD);
                request.setAttribute("lowStockItems", lowStockItems);
                request.setAttribute("totalAlerts", lowStockItems.size());
            }
        } catch (Exception e) {
            request.setAttribute("error", "Error loading inventory data: " + e.getMessage());
            e.printStackTrace();
        }

        request.getRequestDispatcher("/stockalerts.jsp").forward(request, response);
    }

    private List<Item> loadItemsFromFile() throws IOException {
        List<Item> items = new ArrayList<>();
        Path inventoryFile = Paths.get(FilePaths.getDataDirectory(), "inventory.txt");
        File file = inventoryFile.toFile();
        
        if (!file.exists()) {
            throw new IOException("Inventory file not found at: " + file.getAbsolutePath());
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 10) {
                        Item item = new Item();
                        item.setId(Integer.parseInt(parts[0].trim()));
                        item.setName(parts[1].trim());
                        item.setCategory(parts[2].trim());
                        item.setStock(Integer.parseInt(parts[3].trim()));
                        item.setPrice(Double.parseDouble(parts[4].trim()));
                        item.setItemId(parts[5].trim());
                        item.setImagePath(parts[6].trim());
                        item.setExpiryDate(parts[7].trim());
                        item.setAddedDate(parts[8].trim());
                        item.setLastUpdatedDate(parts[9].trim());
                        items.add(item);
                    } else {
                        System.err.println("Warning: Invalid data format at line " + lineNumber + ": " + line);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Invalid number format at line " + lineNumber + ": " + line);
                }
            }
        }
        return items;
    }
}