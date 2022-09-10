package model;

import model.Query.SConnection;

import javax.naming.AuthenticationException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.Date;

/**
 * This static class describes the current session excluding the connection to the Database. The current session includes information and methods
 * about the logged-in user, the Locale, and the ResourceBundle in use.
 */
public class Session extends SConnection {
    private static Integer UserID = null;
    private static String UserName;
    private static PrintStream loginAuditFile;

    private static Locale activeLocale;
    private static ResourceBundle bundle;
    private static final TimeZone timeZone = TimeZone.getDefault();

    static {
        try {
            loginAuditFile = new PrintStream(new FileOutputStream("login_activity.txt", true));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            bundle = ResourceBundle.getBundle("Scheduling", Locale.getDefault());
            // bundle = ResourceBundle.getBundle("Scheduling", Locale.CANADA_FRENCH);
            activeLocale = Locale.getDefault();
        } catch (MissingResourceException mre) {
            activeLocale = Locale.ROOT;
            bundle = ResourceBundle.getBundle("Scheduling", activeLocale);
        }
    }

    public static ResourceBundle getBundle(){
        return bundle;
    }

    public static Locale getActiveLocale(){
        return activeLocale;
    }

    public static String getUserName(){
        if (isLoggedIn()) return UserName;
        else return null;
    }

    public static Integer GetUserID(){
        if (isLoggedIn()) return UserID;
        else return null;
    }

    public static TimeZone getTimeZone(){
        return timeZone;
    }

    public static boolean isLoggedIn(){
        return (UserID != null);
    }

    private static void recordLoginAttempt(String UserName, Boolean success){
        loginAuditFile.println(System.currentTimeMillis() + "," + new Date() + "," + success + "," + UserName);
    }

    /**
     * This method checks if the user can be logged in with the credentials they provided. If so, no errors are thrown.
     * If errors are thrown, they are prepared for the user to see.
     * @param UserName The username to authenticate.
     * @param Password The password to authenticate.
     * @throws AuthenticationException An exception to display to the user, indicating their credentials were not accepted.
     */
    public static void login(String UserName, String Password) throws AuthenticationException {
        if (isLoggedIn()) {
            MessageFormat formatter = new MessageFormat(getBundle().getString("session.already_logged_in{UserName}"));
            throw new AuthenticationException(formatter.format(new Object[]{UserName}));
        }
        PreparedStatement authStatement = statement.getValue();

        try {
            authStatement.setString(1, UserName);
            authStatement.setString(2, Password);
            ResultSet rs = authStatement.executeQuery();

            if (rs.next()) {
                recordLoginAttempt(UserName, true);
                Session.UserID = rs.getInt(1);
                Session.UserName = UserName;
            }
            else {
                recordLoginAttempt(UserName, false);
                throw new AuthenticationException(getBundle().getString("session.credentials_rejected")); // If we don't have a match
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void logout(){
        UserID = null;
    }

    /**
     * This object is used to send queries determining if the given username and password appear in the database.
     */
    private static final Dependable<PreparedStatement> statement = new Dependable<>("conn", getDConn()) {
        @Override
        protected boolean InnerValidate() throws Throwable{
            return !rootObject.isClosed();
        }

        @Override
        protected PreparedStatement InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return ((Connection)depValues.get("conn")).prepareStatement("SELECT User_ID FROM users WHERE User_Name = ? AND Password = ?");
        }
    };
}
