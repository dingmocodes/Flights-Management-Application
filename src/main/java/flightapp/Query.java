package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import flightapp.PasswordUtils;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * about transactions and shit
 * uh yea so like multiple users could be using the application at the same time, so we have to figure out
 * how to deal with that
 * 
 * check out demoT2 for how to deal with transactions
 * 
 * but basically you need to figure out which methods will need to dealt with in terms of having transactions
 * 
 * TA said not to worry about the code when it comes down to it, moreso where you put the code is what's important
 * 
 * Here's the overall format
 * 
 * try {
 *    conn.setAutoCommit(false); // begin transaction
 * 
 *    // some code here
 *    
 *    if things work out
 *      conn.commit(); // end transaction
 *    else they don't work out
 *      conn.rollback(); // end transaction
 * 
 *    conn.setAutoCommit(true);
 * 
 * } catch (SQLException e) {
 *    if (isDeadlock(e)) {
 *        System.out.println("deadlock");
 *        updateDummy(a, conn);
 *    } else {
 *        conn.rollback();
 *        conn.setAutoCommit(true);
 *    }
 * }
 * 
 * 
 * also this was a thing that fucked up past students but you may need to
 * put this try within the else block inside of the catch, just in case that
 * when you rollback, you may run into an error again, so essentially 
 * you might just replace errything in the else block with:
 * 
 *    try {
 *      conn.rollback();
 *    } catch(SQLException se) {
 *      se.printStackTrace();
 *    }
 * 
 * 
 *  Don't forget about deadlocks, you also have a provided method for that
 * 
 * you need to do transactions for any method that reads or writes from or to the users 
 * or reservations table, you don't need to handle transactions for search
 */

/**
 * Runs queries against a back-end database
 */
// use executeQuery for select, executeUpdate for everything else
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private static final String CLEAR_USERS_SQL = "DELETE FROM Users_dingmo";
  private static final String CLEAR_RESERV_SQL = "DELETE FROM Reservations_dingmo";
  private static final String CREATE_CUST_SQL = "INSERT INTO Users_dingmo VALUES (?, ?, ?)";
  private static final String CHECK_USER_SQL = "SELECT COUNT(*) FROM Users_dingmo WHERE username = ?";
  private static final String SEARCH_DIRECT_SQL = "SELECT TOP (?) fid, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price " +
                                                  "FROM Flights " +
                                                  "WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND canceled != 1" +
                                                  "ORDER BY actual_time ASC";
  private static final String SEARCH_SQL = "SELECT TOP (?) f1.fid, f1.day_of_month, f1.carrier_id, f1.flight_num, f1.origin_city, f1.dest_city, f1.actual_time, f1.capacity, f1.price, " +
                                           "f2.fid, f2.day_of_month, f2.carrier_id, f2.flight_num, f2.origin_city, f2.dest_city, f2.actual_time, f2.capacity, f2.price " + 
                                           "FROM Flights f1 " +
                                           "JOIN Flights f2 ON f1.dest_city = f2.origin_city AND " +
                                                               "f1.month_id = f2.month_id AND " +
                                                               "f1.day_of_month = f2.day_of_month AND " +
                                                               "f1.canceled = f2.canceled " +
                                                               // 2nd to last change down here
                                           "WHERE f1.origin_city = ? AND f2.dest_city = ? AND f1.day_of_month = ? AND f1.canceled != 1" +
                                            // last change down here
                                           "ORDER BY (f1.actual_time + f2.actual_time) ASC";
                                           // make sure flight is not cancelled from TA
                                           // do i need to specify f1 or f2 for the last two things in WHERE?
  private static final String GET_PW_SQL = "SELECT password FROM Users_dingmo WHERE username = ?";
  private static final String CHECK_RESERV_SQL = "SELECT * FROM Reservations_dingmo r JOIN Flights f on r.flight1 = f.fid WHERE r.username = ? AND f.day_of_month = ?";
  private static final String ADD_RESERV1_SQL = "INSERT INTO Reservations_dingmo VALUES (?, ?, ?, NULL, ?)";
  private static final String ADD_RESERV2_SQL = "INSERT INTO Reservations_dingmo VALUES (?, ?, ?, ?, ?)";
  private static final String CHECK_CAP_SQL = "SELECT COUNT(*) FROM Reservations_dingmo WHERE flight1 = ?";
  private static final String FIND_RESERV_SQL = "SELECT * FROM Reservations_dingmo WHERE rid = ? AND username = ? AND paid = 0";
  private static final String FIND_RESERV_ORDERED_SQL = "SELECT * FROM Reservations_dingmo WHERE username = ? AND paid = 0 ORDER BY rid ASC";
  private static final String CHECK_BAL_SQL = "SELECT balance FROM Users_dingmo WHERE username = ?";
  private static final String DFLIGHT_COST_SQL = "SELECT price FROM Flights WHERE fid = ?";
  private static final String IDFLIGHT_COST_SQL = "SELECT (f1.price + f2.price) FROM Flights f1 JOIN Flights f2 WHERE f1.fid = ? AND f2.fid = ?";
  private static final String UPDATE_BAL_SQL = "UPDATE Users_dingmo SET balance = ? WHERE username = ?";
  private static final String UPDATE_PAID_SQL = "UPDATE Reservations_dingmo SET paid = 1 WHERE rid = ?";
  private static final String GET_RMAX_SQL = "SELECT COALESCE(MAX(rid), 0) FROM Reservations_dingmo";
  private static final String UPDATE_CAP_SQL = "UPDATE Flights SET capacity = ? WHERE fid = ?";
  private static final String FIND_FLIGHT_SQL = "SELECT * FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;
  private PreparedStatement clearTableStmt;
  private PreparedStatement createCustStmt;
  private PreparedStatement checkUserStmt;
  private PreparedStatement getPWStmt;
  private PreparedStatement searchStmt;
  private PreparedStatement checkReservStmt;
  private PreparedStatement addReservStmt;
  private PreparedStatement checkCapStmt;
  private PreparedStatement findReservStmt;
  private PreparedStatement checkBalStmt;
  private PreparedStatement flightCostStmt;
  private PreparedStatement updateBalStmt;
  private PreparedStatement updatePaidStmt;
  private PreparedStatement getRmaxStmt;
  private PreparedStatement updateCapStmt;
  private PreparedStatement findFlightStmt;

  //
  // Instance variables
  //
  //private Set<String> loggedUsers;
  private boolean userLogged = false;
  private List<Itinerary> iList = new ArrayList<>();
  private String username;
  private List<Reservation> rList = new ArrayList<>();
  // private Reservation ph = new Reservation();
  // rList.add(ph);
  private int reservationId = 0;
  // private 


  protected Query() throws SQLException, IOException {
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearTableStmt = conn.prepareStatement(CLEAR_RESERV_SQL);
      clearTableStmt.executeUpdate();
      clearTableStmt = conn.prepareStatement(CLEAR_USERS_SQL);
      clearTableStmt.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);

    // TODO: YOUR CODE HERE!!!!
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    try {
      conn.setAutoCommit(false);
      if (userLogged) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "User already logged in\n";
      }
      if (username.length() < 1 || password.length() < 1) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Login failed\n";
      }

      getPWStmt = conn.prepareStatement(GET_PW_SQL);
      getPWStmt.clearParameters();
      getPWStmt.setString(1, username);
      ResultSet rs2 = getPWStmt.executeQuery();
      // if rs2 has a next, then user definitely exists
      if (rs2.next()) {
        byte[] dbPassword = rs2.getBytes(1);
        if (PasswordUtils.plaintextMatchesSaltedHash(password, dbPassword)) {
          userLogged = !userLogged;
          this.username = username;
          conn.commit();
          conn.setAutoCommit(true);
          return String.format("Logged in as %s\n", username);
        }
      }
      conn.rollback();
      conn.setAutoCommit(true);
      return "Login failed\n";
    } catch (SQLException e) {
      // e.printStackTrace();
      // return "Login failed\n";
      try {
        conn.rollback();
        if (isDeadlock(e)) {
          System.out.println("deadlock");
          return transaction_login(username, password);
        } else {
          return "Login failed\n";
        }
      } catch (SQLException se){
        e.printStackTrace();
        return "Login failed\n";
      }
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      // p1
      conn.setAutoCommit(false);

      this.username = username;
      checkUserStmt = conn.prepareStatement(CHECK_USER_SQL);
      checkUserStmt.clearParameters();
      checkUserStmt.setString(1, username);
      ResultSet rs = checkUserStmt.executeQuery();
      rs.next();
      int count = rs.getInt(1);
      rs.close();
      if (count > 0 || password.length() == 0 || initAmount < 0) {
        // p2
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }
      createCustStmt = conn.prepareStatement(CREATE_CUST_SQL);
      createCustStmt.clearParameters();
      createCustStmt.setString(1, username);

      byte[] saltAndHashPW = PasswordUtils.saltAndHashPassword(password);
      createCustStmt.setBytes(2, saltAndHashPW);
      
      createCustStmt.setInt(3, initAmount);
      createCustStmt.executeUpdate();

      // p3
      conn.commit();
      conn.setAutoCommit(true);
      return String.format("Created user %s\n", username);
    } catch (SQLException e) {
      // e.printStackTrace();
      // return e.getMessage() + "  Failed to create user\n";
      try {
        conn.rollback();
        if (isDeadlock(e)) {
          System.out.println("deadlock");
          return transaction_createCustomer(username, password, initAmount);
        } else {
          return "Failed to create user\n";
        }
      } catch (SQLException se){
        try {
          conn.rollback();
          conn.setAutoCommit(true);
          if (isDeadlock(e)) {
            return transaction_createCustomer(username, password, initAmount);
          }
        } catch (SQLException exception) {
          exception.printStackTrace();
        }
        e.printStackTrace();
        return "Failed to create user\n";
      }
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // List<Itinerary> iList = new ArrayList<>();
    StringBuffer sb = new StringBuffer();
    try {
      // Obtain all the direct flights
      searchStmt = conn.prepareStatement(SEARCH_DIRECT_SQL);
      searchStmt.clearParameters();
      searchStmt.setInt(1, numberOfItineraries);
      searchStmt.setString(2, originCity);
      searchStmt.setString(3, destinationCity);
      searchStmt.setInt(4, dayOfMonth);
      ResultSet oneHopResults = searchStmt.executeQuery();
      int itineraryNum = 0;

      while (oneHopResults.next()) {
        int result_fid = oneHopResults.getInt("fid");
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("carrier_id");
        String result_flightNum = oneHopResults.getString("flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_time = oneHopResults.getInt("actual_time");
        int result_capacity = oneHopResults.getInt("capacity");
        int result_price = oneHopResults.getInt("price");
        Flight f1 = new Flight(result_fid, result_dayOfMonth, result_carrierId, result_flightNum, result_originCity,
                              result_destCity, result_time, result_capacity, result_price);
        Itinerary i = new Itinerary(f1);
        iList.add(i);

        // dealing with direct flights first
        if (directFlight) {
          sb.append("Itinerary " + itineraryNum + ": 1 flight(s), " + result_time + " minutes" + "\n" + f1.toString() + "\n");
        }
        itineraryNum++;
      }
      oneHopResults.close();

      // check if we're dealing with indirect flights
      if (!directFlight) {
        // if the number of direct flights from earlier is equal to number of intineraries, don't even worry about the indirect stuff
        if (iList.size() == numberOfItineraries) {
          for (int j = 0; j < iList.size(); j++) {
            Itinerary it = iList.get(j);
            Flight f1 = it.f1;
            sb.append("Itinerary " + j + ": 1 flight(s), " + f1.time + " minutes" + "\n" + f1.toString() + "\n");
          }
        // otherwise we have to combine some indirect with direct
        } else {
          searchStmt = conn.prepareStatement(SEARCH_SQL);
          searchStmt.clearParameters();
          searchStmt.setInt(1, numberOfItineraries - iList.size());
          searchStmt.setString(2, originCity);
          searchStmt.setString(3, destinationCity);
          searchStmt.setInt(4, dayOfMonth);
          oneHopResults = searchStmt.executeQuery();

          while (oneHopResults.next()) {
            int f1_result_fid = oneHopResults.getInt(1);
            int f1_result_dayOfMonth = oneHopResults.getInt(2);
            String f1_result_carrierId = oneHopResults.getString(3);
            String f1_result_flightNum = oneHopResults.getString(4);
            String f1_result_originCity = oneHopResults.getString(5);
            String f1_result_destCity = oneHopResults.getString(6);
            int f1_result_time = oneHopResults.getInt(7);
            int f1_result_capacity = oneHopResults.getInt(8);
            int f1_result_price = oneHopResults.getInt(9);
            Flight f1 = new Flight(f1_result_fid, f1_result_dayOfMonth, f1_result_carrierId, f1_result_flightNum, f1_result_originCity,
                                  f1_result_destCity, f1_result_time, f1_result_capacity, f1_result_price);

            int f2_result_fid = oneHopResults.getInt(10);
            int f2_result_dayOfMonth = oneHopResults.getInt(11);
            String f2_result_carrierId = oneHopResults.getString(12);
            String f2_result_flightNum = oneHopResults.getString(13);
            String f2_result_originCity = oneHopResults.getString(14);
            String f2_result_destCity = oneHopResults.getString(15);
            int f2_result_time = oneHopResults.getInt(16);
            int f2_result_capacity = oneHopResults.getInt(17);
            int f2_result_price = oneHopResults.getInt(18);
            Flight f2 = new Flight(f2_result_fid, f2_result_dayOfMonth, f2_result_carrierId, f2_result_flightNum, f2_result_originCity,
                                  f2_result_destCity, f2_result_time, f2_result_capacity, f2_result_price);
            Itinerary i = new Itinerary(f1, f2);
            iList.add(i);
            /**
             * 
             */
          }
          oneHopResults.close();
          // sort the list of itineraries
          Collections.sort(iList);
          for (int i = 0; i < numberOfItineraries; i++) {
            Itinerary it = iList.get(i);
            // check if itinerary is a direct flight
            if (it.df) {
              Flight f1 = it.f1;
              sb.append("Itinerary " + i + ": 1 flight(s), " + f1.time + " minutes" + "\n" + f1.toString() + "\n");
            // otherwise it's indirect
            } else {
              Flight f1 = it.f1;
              Flight f2 = it.f2;
                                                              // made a change here
              sb.append("Itinerary " + i + ": 2 flight(s), " + (f1.time + f2.time) + " minutes" + "\n" + f1.toString() + "\n" + f2.toString() + "\n");
            }
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return "you failed somewhere";
    }
    return sb.toString();
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    try {
      conn.setAutoCommit(false);
      // user not logged in case
      if (!userLogged) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot book reservations, not logged in\n";
      }

      // if itineraries list is empty or user did not provide a valid itinerary id case
      if ((iList.isEmpty()) || (itineraryId < 0 || itineraryId > iList.size() - 1)) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "No such itinerary " + itineraryId + "\n";
      }

      // get the itinerary that the user wants
      Itinerary i = iList.get(itineraryId);
      Flight f = i.f1;
      Flight f2 = null;

      // check if this itinerary of interest is a df
      if (i.df) {
        // check flight capacity
        checkCapStmt = conn.prepareStatement(CHECK_CAP_SQL);
        checkCapStmt.clearParameters();
        checkCapStmt.setInt(1, f.fid);
        ResultSet rs1 = checkCapStmt.executeQuery();
        int seatsTaken = 0;
        if (rs1.next()) {
          seatsTaken = rs1.getInt(1);
        }
        if ((f.capacity - seatsTaken) < 1) {
          conn.rollback();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
        // } else {
        //   // if there is space, we need to update the capacity
        //   updateCapStmt = conn.prepareStatement(UPDATE_CAP_SQL);
        //   updateCapStmt.setInt(1, f.capacity - seatsTaken);
        //   updateCapStmt.setInt(2, f.fid);
        //   updateCapStmt.executeUpdate();
        //   f.capacity -= seatsTaken;
        //}

        // check if the itinerary that the user wants is on the same day
        // as another reserved itinerary
        checkReservStmt = conn.prepareStatement(CHECK_RESERV_SQL);
        checkReservStmt.clearParameters();
        checkReservStmt.setString(1, username);
        checkReservStmt.setInt(2, f.dayOfMonth);
        //checkReservStmt.setInt(3, f.fid);
        ResultSet rs2 = checkReservStmt.executeQuery();
        if (rs2.next()) {
          conn.rollback();
          conn.setAutoCommit(true);
          return "You cannot book two flights in the same day\n";
        }
      // otherwise the itinerary contains an indirect flight, check capacity for it
      // no need to check day since it should be same as first flight
      } else {
        // do i need to clear parameters by chance?
        f2 = i.f2;
        // check flight capacity
        checkCapStmt = conn.prepareStatement(CHECK_CAP_SQL);
        checkCapStmt.clearParameters();
        checkCapStmt.setInt(1, f2.fid);
        ResultSet rs1 = checkCapStmt.executeQuery();
        int seatsTaken = 0;
        if (rs1.next()) {
          seatsTaken = rs1.getInt(1);
        }
        if ((f2.capacity - seatsTaken) < 1) {
          conn.rollback();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
        // } else {
        //   // if there is space, we need to update the capacity
        //   updateCapStmt = conn.prepareStatement(UPDATE_CAP_SQL);
        //   updateCapStmt.setInt(1, f2.capacity - seatsTaken);
        //   updateCapStmt.setInt(2, f2.fid);
        //   updateCapStmt.executeUpdate();
        //   f2.capacity -= seatsTaken;
        // }
      }
      // update the itinerary id
      // NEED TO CHANGE THIS: could cause issues, grab max from tables instead (for transactions)
      getRmaxStmt = conn.prepareStatement(GET_RMAX_SQL);
      ResultSet rs2 = getRmaxStmt.executeQuery();
      rs2.next();
      int reservationId = rs2.getInt(1) + 1;
      // add reservation to the table
      if (f2 == null) {
        addReservStmt = conn.prepareStatement(ADD_RESERV1_SQL);
        addReservStmt.clearParameters();
        addReservStmt.setInt(1, reservationId);
        addReservStmt.setString(2, this.username);
        addReservStmt.setInt(3, f.fid);
        addReservStmt.setInt(4, 0);
        Reservation r = new Reservation(reservationId, this.username, f, false, itineraryId);
        rList.add(r);
      } else {
        addReservStmt = conn.prepareStatement(ADD_RESERV2_SQL);
        addReservStmt.clearParameters();
        addReservStmt.setInt(1, reservationId);
        addReservStmt.setString(2, this.username);
        addReservStmt.setInt(3, f.fid);
        addReservStmt.setInt(4, f2.fid);
        addReservStmt.setInt(5, 0);
        Reservation r = new Reservation(reservationId, this.username, f, f2, false, itineraryId);
        rList.add(r);
      }
      addReservStmt.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return "Booked flight(s), reservation ID: " + reservationId + "\n";
    } catch (SQLException e) {
      // e.printStackTrace();
      // return "Booking failed\n";
      e.printStackTrace();
        if (isDeadlock(e)) {
          return transaction_book(itineraryId);
        }
    }
    return "Booking failed\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    /**
     * if not logged in
     *    return "Cannot pay, not logged in\n"
     * 
     * if we can't find the reservation under the user OR it's already paid
     *    return "Cannot find unpaid reservation [reservationId] under user: [username]\n"
     * 
     * if user does not have enough money to pay for reservation
     *    return "User has only [balance] in account but itinerary costs [cost]\n"
     * 
     * otherwise when it's successful
     *    return "Paid reservation: [reservationId] remaining balance: [balance]\n"
     * 
     */
    try {
      conn.setAutoCommit(false);
      // check if user is logged in
      if (!userLogged) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot pay, not logged in\n";
      }
      
      // returns a tuple if we find the user's reservation
      // Add a WHERE to make sure that we only get back reservation tuples that also aren't paid
      findReservStmt = conn.prepareStatement(FIND_RESERV_SQL);
      findReservStmt.clearParameters();
      findReservStmt.setInt(1, reservationId);
      findReservStmt.setString(2, this.username);
      ResultSet reservResult = findReservStmt.executeQuery();

      // if rs.next(), then a reservation does exist
      // BUG: rs.next() not working? -> Query works for sure
      if (reservResult.next()) {
        // we know a reservation exists now and it hasn't been paid yet
        // returns user's balance
        checkBalStmt = conn.prepareStatement(CHECK_BAL_SQL);
        checkBalStmt.clearParameters();
        checkBalStmt.setString(1, username);
        ResultSet balResult = checkBalStmt.executeQuery();
        balResult.next();

        // keep track of balance to return later
        int balance = 0;

        // check if reserved itinerary contains an indirect flight (check if flight2 has a valid fid)
        // i.d. flight
        if (reservResult.getInt(4) > 0) {
          flightCostStmt = conn.prepareStatement(IDFLIGHT_COST_SQL);
          flightCostStmt.clearParameters();
          flightCostStmt.setInt(1, reservResult.getInt(3));
          flightCostStmt.setInt(2, reservResult.getInt(4));
          ResultSet fCostResult = flightCostStmt.executeQuery();
          //
          fCostResult.next();
          // check if this foo can pay for both flights
          if (balResult.getInt(1) >= fCostResult.getInt(1)) {
            balance = balResult.getInt(1) - fCostResult.getInt(1);
            updateBalStmt = conn.prepareStatement(UPDATE_BAL_SQL);
            updateBalStmt.clearParameters();
            updateBalStmt.setInt(1, balance);
            updateBalStmt.setString(2, this.username);
            updateBalStmt.executeUpdate();
            updatePaidStmt = conn.prepareStatement(UPDATE_PAID_SQL);
            updatePaidStmt.clearParameters();
            updatePaidStmt.setInt(1, reservationId);
            updatePaidStmt.executeUpdate();
          } else {
            conn.rollback();
            conn.setAutoCommit(true);
            return "User has only " + balResult.getInt(1) + " in account but itinerary costs " + fCostResult.getInt(1) + "\n";
          }
        // d flight
        } else {
          // rs.next();
          flightCostStmt = conn.prepareStatement(DFLIGHT_COST_SQL);
          flightCostStmt.clearParameters();
          flightCostStmt.setInt(1, reservResult.getInt(3));
          ResultSet fCostResult = flightCostStmt.executeQuery();
          //
          fCostResult.next();
          // check if this foo can pay for the flight
          if (balResult.getInt(1) >= fCostResult.getInt(1)) {
            balance = balResult.getInt(1) - fCostResult.getInt(1);
            updateBalStmt = conn.prepareStatement(UPDATE_BAL_SQL);
            updateBalStmt.clearParameters();
            updateBalStmt.setInt(1, balance);
            updateBalStmt.setString(2, this.username);
            updateBalStmt.executeUpdate();
            updatePaidStmt = conn.prepareStatement(UPDATE_PAID_SQL);
            updatePaidStmt.clearParameters();
            updatePaidStmt.setInt(1, reservationId);
            updatePaidStmt.executeUpdate();
          } else {
            conn.rollback();
            conn.setAutoCommit(true);
            return "User has only " + balResult.getInt(1) + " in account but itinerary costs " + fCostResult.getInt(1) + "\n";
          }
        }
        conn.commit();
        conn.setAutoCommit(true);
        return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
      } else {
        // no reservation was found
        // System.out.println("uh so i guess the rs.next() didn't work");
        conn.rollback();
        return "Cannot find unpaid reservation " + reservationId + " under user: " + username + "\n";
      }
    } catch (SQLException e) {
      try {
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_pay(reservationId);
        }
      } catch (SQLException exception) {
        exception.printStackTrace();
      }
      e.printStackTrace();
      // return e.getMessage();
    }
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
  /**
   * Prints out reserved itineraries, regardless of their payment status.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" 
   * 
   *         If the user has no reservations, then return "No reservations found\n" 
   * 
   *         For all other errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n 
   *         [flight 1 under the reservation]\n 
   *         [flight 2 under the reservation]\n 
   * 
   *         Reservation [reservation ID] paid: [true or false]:\n 
   *         [flight 1 under the reservation]\n 
   *         [flight 2 under the reservation]\n ...
   *
   *         Each flight should be printed using the same format as in
   *         the {@code Query.Flight} class.
   *
   * @see Query.Flight#toString()
   */
    try {
      conn.setAutoCommit(false);
      StringBuffer sb = new StringBuffer();
      if (!userLogged) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot view reservations, not logged in\n";
      }

      // check if this foo even has a reservation
      findReservStmt = conn.prepareStatement(FIND_RESERV_ORDERED_SQL);
      //findReservStmt.setInt(1, reservationId);
      findReservStmt.clearParameters();
      findReservStmt.setString(1, this.username);
      ResultSet reservResults = findReservStmt.executeQuery();
  
      if (!reservResults.isBeforeFirst()) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      } else {
        // otherwise we know they have a reservation at this point
        /**
         * for each tuples that is returned (# of reservations for a user),
         *    if flight2 is null
         *       save the attributes: rid and paid
         *       add to sb: Reservation [reservation ID] paid: [true or false]:\n 
         *       get reservation w/ rid from list
         *       get iid from reservation
         *       get itinerary w/ iid from list
         *       get f1 from itinerary
         *       add to sb: f1.toString
         *    else 
         *       save the attributes: rid and paid
         *       add to sb: Reservation [reservation ID] paid: [true or false]:\n 
         *       get reservation w/ rid from list
         *       get iid from reservation
         *       get itinerary w/ iid from list
         *       get f1 from itinerary
         *       get f2 from itinerary
         *       add to sb: f1.toString + /n + f2.toString
         * 
         */
        while (reservResults.next()) {
          int f1id = reservResults.getInt(3);
          int f2id = reservResults.getInt(4);
          int rid = reservResults.getInt(1);
          boolean paid = reservResults.getBoolean(5);
          if (f2id > 0) {
            // at this point we know we're dealing with an f2
            findFlightStmt = conn.prepareStatement(FIND_FLIGHT_SQL);
            findFlightStmt.clearParameters();
            findFlightStmt.setInt(1, f1id);
            ResultSet flightData = findFlightStmt.executeQuery();
            flightData.next();
            int f1_result_fid = flightData.getInt("fid");
            int f1_result_dayOfMonth = flightData.getInt("day_of_month");
            String f1_result_carrierId = flightData.getString("carrier_id");
            String f1_result_flightNum = flightData.getString("flight_num");
            String f1_result_originCity = flightData.getString("origin_city");
            String f1_result_destCity = flightData.getString("dest_city");
            int f1_result_time = flightData.getInt("actual_time");
            int f1_result_capacity = flightData.getInt("capacity");
            int f1_result_price = flightData.getInt("price");
            Flight f1 = new Flight(f1_result_fid, f1_result_dayOfMonth, f1_result_carrierId, f1_result_flightNum, f1_result_originCity,
                                  f1_result_destCity, f1_result_time, f1_result_capacity, f1_result_price);

            //findFlightStmt = conn.prepareStatement(FIND_FLIGHT_SQL);
            findFlightStmt.clearParameters();
            findFlightStmt.setInt(1, f2id);
            flightData = findFlightStmt.executeQuery();
            flightData.next();
            int f2_result_fid = flightData.getInt("fid");
            int f2_result_dayOfMonth = flightData.getInt("day_of_month");
            String f2_result_carrierId = flightData.getString("carrier_id");
            String f2_result_flightNum = flightData.getString("flight_num");
            String f2_result_originCity = flightData.getString("origin_city");
            String f2_result_destCity = flightData.getString("dest_city");
            int f2_result_time = flightData.getInt("actual_time");
            int f2_result_capacity = flightData.getInt("capacity");
            int f2_result_price = flightData.getInt("price");
            Flight f2 = new Flight(f2_result_fid, f2_result_dayOfMonth, f2_result_carrierId, f2_result_flightNum, f2_result_originCity,
                                  f2_result_destCity, f2_result_time, f2_result_capacity, f2_result_price);
            sb.append("Reservation " + rid + " paid: " + paid + ":\n");
            sb.append(f1.toString() + "\n");
            sb.append(f2.toString() + "\n");
          } else {
            // at this point we don't have an f2
            findFlightStmt = conn.prepareStatement(FIND_FLIGHT_SQL);
            findFlightStmt.clearParameters();
            findFlightStmt.setInt(1, f1id);
            ResultSet flightData = findFlightStmt.executeQuery();
            flightData.next();
            int f1_result_fid = flightData.getInt("fid");
            int f1_result_dayOfMonth = flightData.getInt("day_of_month");
            String f1_result_carrierId = flightData.getString("carrier_id");
            String f1_result_flightNum = flightData.getString("flight_num");
            String f1_result_originCity = flightData.getString("origin_city");
            String f1_result_destCity = flightData.getString("dest_city");
            int f1_result_time = flightData.getInt("actual_time");
            int f1_result_capacity = flightData.getInt("capacity");
            int f1_result_price = flightData.getInt("price");
            Flight f1 = new Flight(f1_result_fid, f1_result_dayOfMonth, f1_result_carrierId, f1_result_flightNum, f1_result_originCity,
                                  f1_result_destCity, f1_result_time, f1_result_capacity, f1_result_price);
            sb.append("Reservation " + rid + " paid: " + paid + ":\n");
            sb.append(f1.toString() + "\n");
          }
        }
      }
      conn.commit();
      conn.setAutoCommit(true);
      return sb.toString();
    } catch (SQLException e) {
      if (isDeadlock(e)) {
        return transaction_reservations();
      }
      try {
        conn.rollback();
        conn.setAutoCommit(true);
      } catch (SQLException exec) {
        exec.printStackTrace();
      }
    }
    return "Failed to retrieve reservations\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   *
   * TODO(hctang): move this into QueryAbstract
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }

  class Itinerary implements Comparable<Itinerary> {
    public boolean df;
    public Flight f1;
    public int f1Time;
    public int f1Fid;
    public Flight f2;
    public int f2Time;
    public int f2Fid;

    // Direct flight itinerary
    Itinerary(Flight f1) {
      this.df = true;
      this.f1 = f1;
      this.f1Time = f1.time;
      this.f1Fid = f1.fid;
    }

    // Indirect flight itinerary
    Itinerary(Flight f1, Flight f2) {
      this.df = false;
      this.f1 = f1;
      this.f1Time = f1.time;
      this.f1Fid = f1.fid;
      this.f2 = f2;
      this.f2Time = f2.time;
      this.f2Fid = f2.fid;
    }

    @Override
    public int compareTo(Itinerary other) {
      // both Itineraries are direct flights
      if (this.df && other.df) {
        if (this.f1Time < other.f1Time) {
          return -1;
        } else if (this.f1Time > other.f1Time) {
          return 1;
        } else {
          // this check might not work for all cases
          if (this.f1Fid < other.f1Fid) {
            return -1;
          } else {
            return 1;
          }
        }
      // this Itinerary is direct but Other is indirect
      } else if (this.df && !other.df) {
        int otherTime = other.f1Time + other.f2Time;
        if (this.f1Time < otherTime) {
          return -1;
        } else if (this.f1Time > otherTime) {
          return 1;
        } else {
          if (this.f1Fid < other.f1Fid) {
            return -1;
          } else {
            return 1;
          }
        }
      // this Itinerary is indirect but Other is direct
      } else if (!this.df && other.df) {
        int thisTime = this.f1Time + this.f2Time;
        if (thisTime < other.f1Time) {
          return -1;
        } else if (thisTime > other.f1Time) {
          return 1;
        } else {
          if (this.f1Fid < other.f1Fid) {
            return -1;
          } else {
            return 1;
          }
        }
      // both itineraries are indirect
      } else { // !this.df && !other.df
        int thisTime = this.f1Time + this.f2Time;
        int otherTime = other.f1Time + other.f2Time;
        if (thisTime < otherTime) {
          return -1;
        } else if (thisTime > otherTime) {
          return 1;
        } else {
          if (this.f1Fid < other.f1Fid) {
            return -1;
          } else {
            return 1;
          }
        }
      }
    }
  }

  class Reservation {
    public int rid;
    public String username;
    public Flight f1;
    public Flight f2;
    public boolean paid;
    public int iid;

    Reservation(int rid, String username, Flight f1, boolean paid, int iid) {
      this.rid = rid;
      this.username = username;
      this.f1 = f1;
      this.paid = paid;
      this.iid = iid;
    }

    Reservation(int rid, String username, Flight f1, Flight f2, boolean paid, int iid) {
      this.rid = rid;
      this.username = username;
      this.f1 = f1;
      this.f2 = f2;
      this.paid = paid;
      this.iid = iid;
    }

    Reservation(){}
  }

}
