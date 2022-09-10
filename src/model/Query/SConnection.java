package model.Query;

import model.Dependable;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.Map;
import java.util.function.Function;

// TODO: REQUIRED: Configure Connection via file

/**
 * The SConnection class is a shared configuration class which provides a shared {@link Connection} via {@link #getDConn()}.
 * The SConnection class also manages shared {@link DatabaseMetaData} via {@link #getDConnMetaData()}.
 */
public class SConnection {
    private static final String catalog = "client_schedule";

    protected static String getCatalog(){
        return catalog;
    }

    private static final Dependable<Connection> conn = new Dependable<>() {
        @Override
        protected boolean InnerValidate() throws Throwable {
            return rootObject.isValid(5);
        }

        /**
         * Grab, parse, and construct a connection string based on an application-external `database.xml` file.<br>
         * If, during use of this application, the configuration of the server changes, the database.xml file will be
         * re-parsed until a functioning connection is established, meaning the application can durably swap between different
         * underlying databases.
         *
         * @return A URI-structured String describing a connection to a Database.
         * @throws Exception IO-like exceptions in extracting from database.xml
         */
        private String connectionStringFromFile() throws Exception {
            File databaseConfigFile = new File("database.xml");
            Document databaseConfigDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(databaseConfigFile);
            databaseConfigDoc.getDocumentElement().normalize();

            Function<String, String> getDBParam = (String name) -> databaseConfigDoc.getElementsByTagName(name).item(0).getTextContent();

            String serverName = getDBParam.apply("server");
            int port = Integer.parseInt(getDBParam.apply("port"));
            String name = getDBParam.apply("name");

            String user = getDBParam.apply("user");
            String password = getDBParam.apply("password");

            String query = String.format("user=%s&password=%s", user, password);

            return new URI("jdbc:mysql", null, serverName, port, "/" + name, query, null).toString();
        }

        @Override
        protected Connection InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return DriverManager.getConnection(connectionStringFromFile());
        }
    };

    public static Dependable<Connection> getDConn(){
        return conn;
    }

    private static final Dependable<DatabaseMetaData> connMetaData = new Dependable<>("conn", conn) {
        @Override
        protected boolean InnerValidate() throws Throwable {
            return true;
        }

        @Override
        protected DatabaseMetaData InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return ((Connection)depValues.get("conn")).getMetaData();
        }
    };

    public static Dependable<DatabaseMetaData> getDConnMetaData(){
        return connMetaData;
    }


}
