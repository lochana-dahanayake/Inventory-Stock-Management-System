package com.ims185.servlet;

import com.ims185.model.User;
import com.ims185.util.FileStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

//@WebServlet("/signup")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 2, // 2MB
        maxFileSize = 1024 * 1024 * 10,     // 10MB
        maxRequestSize = 1024 * 1024 * 50   // 50MB
)
public class SignupServlet extends HttpServlet {
    private static final String UPLOAD_DIR = "Uploads";
    private static final String DATA_DIR = "C:/IMS-185-Data";
    private static final Logger LOGGER = Logger.getLogger(SignupServlet.class.getName());

    static {
        try {
            // Initialize logger
            File logDir = new File(DATA_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            FileHandler fileHandler = new FileHandler(DATA_DIR + "/signup_errors.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            // Initialize upload directory
            File uploadDir = new File(DATA_DIR + File.separator + UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            // Verify upload directory
            File uploadDir = new File(DATA_DIR + File.separator + UPLOAD_DIR);
            if (!uploadDir.exists() || !uploadDir.isDirectory()) {
                LOGGER.severe("Upload directory does not exist or is not a directory: " + uploadDir.getAbsolutePath());
                throw new ServletException("Upload directory is invalid");
            }
            if (!uploadDir.canWrite()) {
                LOGGER.severe("No write permission for upload directory: " + uploadDir.getAbsolutePath());
                throw new ServletException("Cannot write to upload directory");
            }

            // Validate form parameters
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            String email = request.getParameter("email");
            String phone = request.getParameter("phone");
            Part imagePart = request.getPart("image");

            if (username == null || username.trim().isEmpty() ||
                    password == null || password.trim().isEmpty() ||
                    email == null || email.trim().isEmpty() ||
                    phone == null || phone.trim().isEmpty()) {
                LOGGER.warning("Missing required fields: username=" + username + ", email=" + email + ", phone=" + phone);
                throw new ServletException("All fields are required");
            }

            // Check username availability
            try {
                if (FileStorage.userExists(username)) {
                    LOGGER.warning("Username already taken: " + username);
                    throw new ServletException("Username already taken");
                }
            } catch (Exception e) {
                LOGGER.severe("Error checking username existence: " + e.getMessage());
                throw new ServletException("Failed to check username availability", e);
            }

            // Determine role (first user is admin)
            String role = FileStorage.getAllUsers().isEmpty() ? "admin" : "user";
            LOGGER.info("Assigning role: " + role + " to user: " + username);

            // Handle image upload
            String imagePath = "default.jpg";
            if (imagePart != null && imagePart.getSize() > 0) {
                try {
                    String fileName = UUID.randomUUID().toString() + "_" + imagePart.getSubmittedFileName();
                    String fullPath = DATA_DIR + File.separator + UPLOAD_DIR + File.separator + fileName;
                    imagePart.write(fullPath);
                    imagePath = UPLOAD_DIR + File.separator + fileName;
                    LOGGER.info("Image uploaded: " + imagePath);
                } catch (Exception e) {
                    LOGGER.severe("Image upload failed: " + e.getMessage());
                    throw new ServletException("Failed to upload image", e);
                }
            }

            // Create user
            User user;
            try {
                user = new User(
                        UUID.randomUUID().toString(),
                        username,
                        password,
                        imagePath,
                        true,
                        LocalDateTime.now(),
                        email,
                        phone,
                        role
                );
            } catch (Exception e) {
                LOGGER.severe("User creation failed: " + e.getMessage());
                throw new ServletException("Failed to create user", e);
            }

            // Save user
            try {
                FileStorage.saveUser(user);
                LOGGER.info("User saved: " + username + " with role: " + role);
            } catch (Exception e) {
                LOGGER.severe("User save failed: " + e.getMessage());
                throw new ServletException("Failed to save user", e);
            }

            // Redirect to login
            response.sendRedirect(request.getContextPath() + "/login");
        } catch (Exception e) {
            String errorMessage = "Registration failed: " + e.getMessage();
            LOGGER.severe("Registration failed for username=" + request.getParameter("username") + ": " + errorMessage);
            e.printStackTrace();
            String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
            response.sendRedirect(request.getContextPath() + "/signup?error=" + encodedError);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/signup.jsp").forward(request, response);
    }
}