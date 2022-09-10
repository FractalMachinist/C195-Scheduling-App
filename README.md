# Scheduling Software 
(Purpose: Competency Demonstration) Submission v2022-03-29.2  
Written by Zachary Allen zallen7@wgu.edu  
Written using  
- IntelliJ IDEA 2021.3.3 (Community Edition)
- JDK 17.0.2
- MySQL-connector-java-8.0.22
- JavaFX-SDK-11.0.2

# Summary
This program was written as a class project to provide an effective frontend UI to a pre-existing customer database. The program is written with JavaFX and a MySQL backend. The feature I enjoyed writing most was the ['Dependable' base class](/src/model/Dependable.java) ([documented here]([TMP_LINK](https://fractalmachini.st/C195-Scheduling-App/model/Dependable.html))), which ensures the various Observable states in the application are proactively (rather than passively) up to date with respect to a live database connection.

# USAGE
These instructions were written with a class evaluator in mind, based on the assumption of a configured database and runtime environment.
1. Configure the provided `database.xml` to match your required connection specifications.
2. Launch the application and log in.
3. To edit the attributes of an Appointment or Customer, double-click the field you wish to edit, perform the desired edits, and press enter to save the edits locally.
	- To permanently save or to clear your edits, or to delete the selected entry, use the so-labeled buttons on the bottom of the window.
4. Similarly, rows marked with '[New Row]' or similar are local rows which, when populated and saved, are INSERTed into the Database.
