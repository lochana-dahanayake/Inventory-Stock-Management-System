package com.ims185.servlet;

import com.ims185.model.Item;
import com.ims185.config.FilePaths;
import com.ims185.util.ActivityLogger;
import com.ims185.util.Stack;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;





import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,  // 1 MB
        maxFileSize = 1024 * 1024 * 10,   // 10 MB
        maxRequestSize = 1024 * 1024 * 50 // 50 MB
)
public class InventoryServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InventoryServlet.class.getName());

    // Load items into a Stack instead of List
    private Stack<Item> loadItemsFromFile() {
        Stack<Item> stack = new Stack<>();
        Path inventoryFile = Paths.get(FilePaths.getDataDirectory(), "inventory.txt");
        File file = inventoryFile.toFile();

        if (!file.getParentFile().exists()) {
            boolean created = file.getParentFile().mkdirs();
            LOGGER.info("Created directory " + file.getParentFile().getPath() + ": " + created);
        }

        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    LOGGER.info("Created inventory.txt");
                }
            } catch (IOException e) {
                LOGGER.severe("Failed to create inventory.txt: " + e.getMessage());
                return stack;
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 10) {
                    try {
                        Item item = new Item();
                        item.setId(Integer.parseInt(parts[0]));
                        item.setName(parts[1]);
                        item.setCategory(parts[2]);
                        item.setStock(Integer.parseInt(parts[3]));
                        item.setPrice(Double.parseDouble(parts[4]));
                        item.setItemId(parts[5]);
                        item.setImagePath(parts[6]);
                        item.setExpiryDate(parts[7]);
                        item.setAddedDate(parts[8]);
                        item.setLastUpdatedDate(parts[9]);
                        stack.push(item);
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Skipping line due to parsing error: " + line);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Error reading inventory.txt: " + e.getMessage());
        }
        return stack;
    }

    // Save from Stack to file (write in the order they are stacked)
    private void saveItemsToFile(Stack<Item> stack) {
        Path inventoryFile = Paths.get(FilePaths.getDataDirectory(), "inventory.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(inventoryFile.toFile()))) {
            for (Item item : stack.getAll()) {
                writer.write(String.format("%d,%s,%s,%d,%.2f,%s,%s,%s,%s,%s%n",
                        item.getId(),
                        item.getName(),
                        item.getCategory() != null ? item.getCategory() : "",
                        item.getStock(),
                        item.getPrice(),
                        item.getItemId(),
                        item.getImagePath() != null ? item.getImagePath() : "",
                        item.getExpiryDate() != null ? item.getExpiryDate() : "",
                        item.getAddedDate() != null ? item.getAddedDate() : "",
                        item.getLastUpdatedDate() != null ? item.getLastUpdatedDate() : ""));
            }
            LOGGER.info("Saved " + stack.size() + " items to inventory.");
        } catch (IOException e) {
            LOGGER.severe("Error saving inventory: " + e.getMessage());
        }
    }

    private String saveImage(Part filePart, String itemId) {
        if (filePart != null && filePart.getSize() > 0) {
            try {
                String fileName = itemId + "_" + UUID.randomUUID().toString() + ".jpg";
                Path imagesPath = Paths.get(FilePaths.getUploadDirectory(), "images");
                if (!imagesPath.toFile().exists()) {
                    imagesPath.toFile().mkdirs();
                }
                String contentType = filePart.getContentType();
                String extension = contentType != null && contentType.contains("/")
                        ? contentType.split("/")[1]
                        : "jpg";
                fileName = fileName.replace(".jpg", "." + extension);
                Path filePath = imagesPath.resolve(fileName);
                filePart.write(filePath.toString());
                return "images/" + fileName;
            } catch (IOException e) {
                LOGGER.severe("Error saving image: " + e.getMessage());
            }
        }
        return "";
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        if (session.getAttribute("loggedInUser") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Stack<Item> stack = loadItemsFromFile();
        List<Item> items = stack.getAll();

        // Sort if requested
        String sortBy = request.getParameter("sort");
        if ("expiry".equals(sortBy)) {
            items = sortItemsByExpiryDate(items);
        }

        // Filter by search query
        final String searchQuery = request.getParameter("search");
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            final String query = searchQuery.toLowerCase();
            items = items.stream()
                    .filter(item ->
                            (item.getName() != null && item.getName().toLowerCase().contains(query)) ||
                                    (item.getItemId() != null && item.getItemId().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        request.setAttribute("items", items);
        request.getRequestDispatcher("/inventory.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        if (session.getAttribute("loggedInUser") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String action = request.getParameter("action");
        Stack<Item> stack = loadItemsFromFile();
        List<Item> items = stack.getAll();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        try {
            if ("Add".equals(action)) {
                Item item = new Item();
                item.setId(items.isEmpty() ? 1 : items.stream().mapToInt(Item::getId).max().getAsInt() + 1);
                item.setName(request.getParameter("name"));
                item.setCategory(request.getParameter("category"));
                item.setStock(Integer.parseInt(request.getParameter("stock")));
                item.setPrice(Double.parseDouble(request.getParameter("price")));
                item.setItemId(request.getParameter("itemId"));
                item.setExpiryDate(request.getParameter("expiryDate"));
                item.setAddedDate(dateFormat.format(new Date()));
                item.setLastUpdatedDate(dateFormat.format(new Date()));

                Part filePart = request.getPart("image");
                String imagePath = saveImage(filePart, item.getItemId());
                item.setImagePath(imagePath);

                stack.push(item);
                LOGGER.info("Added new item: " + item.getName());

                ActivityLogger.logInventoryAction(request, "added", item.getName(), item.getStock());
            } else if ("Update".equals(action)) {
                int id = Integer.parseInt(request.getParameter("id"));
                Item existingItem = null;

                // Find existing item
                for (Item i : stack.getAll()) {
                    if (i.getId() == id) {
                        existingItem = i;
                        break;
                    }
                }

                if (existingItem != null) {
                    existingItem.setName(request.getParameter("name"));
                    existingItem.setCategory(request.getParameter("category"));
                    existingItem.setStock(Integer.parseInt(request.getParameter("stock")));
                    existingItem.setPrice(Double.parseDouble(request.getParameter("price")));
                    existingItem.setItemId(request.getParameter("itemId"));
                    existingItem.setExpiryDate(request.getParameter("expiryDate"));
                    existingItem.setLastUpdatedDate(dateFormat.format(new Date()));

                    Part filePart = request.getPart("image");
                    if (filePart != null && filePart.getSize() > 0) {
                        String imagePath = saveImage(filePart, existingItem.getItemId());
                        existingItem.setImagePath(imagePath);
                    }

                    // Move updated item to top of stack to simulate latest update on top
                    stack.moveToTop(existingItem);

                    LOGGER.info("Updated item: " + existingItem.getName());
                    ActivityLogger.logInventoryAction(request, "updated", existingItem.getName(), existingItem.getStock());
                }
            } else if ("Delete".equals(action)) {
                int id = Integer.parseInt(request.getParameter("id"));
                Item itemToDelete = null;

                for (Item i : stack.getAll()) {
                    if (i.getId() == id) {
                        itemToDelete = i;
                        break;
                    }
                }

                if (itemToDelete != null) {
                    stack.getAll().remove(itemToDelete); // Remove from underlying list
                    // But better remove from stack directly:
                    // Since Stack class does not have remove, use getAll(), remove and reconstruct stack:
                    Stack<Item> newStack = new Stack<>();
                    for (Item i : stack.getAll()) {
                        if (i.getId() != id) {
                            newStack.push(i);
                        }
                    }
                    stack = newStack;

                    LOGGER.info("Deleted item with ID: " + id);
                    ActivityLogger.logInventoryAction(request, "deleted", itemToDelete.getName(), null);
                }
            }

            saveItemsToFile(stack);

        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid number format: " + e.getMessage());
            request.setAttribute("error", "Invalid number format in the form. Please check your input.");
            request.setAttribute("items", stack.getAll());
            request.getRequestDispatcher("/inventory.jsp").forward(request, response);
            return;
        } catch (Exception e) {
            LOGGER.severe("Error processing inventory action: " + e.getMessage());
            request.setAttribute("error", "An error occurred while processing your request.");
            request.setAttribute("items", stack.getAll());
            request.getRequestDispatcher("/inventory.jsp").forward(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/inventory");
    }

    private List<Item> sortItemsByExpiryDate(List<Item> items) {
        if (items.size() <= 1) return items;

        int mid = items.size() / 2;
        List<Item> left = sortItemsByExpiryDate(new ArrayList<>(items.subList(0, mid)));
        List<Item> right = sortItemsByExpiryDate(new ArrayList<>(items.subList(mid, items.size())));

        return merge(left, right);
    }

    private List<Item> merge(List<Item> left, List<Item> right) {
        List<Item> result = new ArrayList<>();
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            String dateA = left.get(i).getExpiryDate() != null ? left.get(i).getExpiryDate() : "9999-12-31";
            String dateB = right.get(j).getExpiryDate() != null ? right.get(j).getExpiryDate() : "9999-12-31";

            if (dateA.compareTo(dateB) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }

        while (i < left.size()) result.add(left.get(i++));
        while (j < right.size()) result.add(right.get(j++));

        return result;
    }
}
