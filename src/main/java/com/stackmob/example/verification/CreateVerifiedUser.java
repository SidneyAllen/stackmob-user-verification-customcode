/**
 * Copyright 2012-2013 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stackmob.example;

import com.stackmob.core.customcode.CustomCodeMethod;
import com.stackmob.core.rest.ProcessedAPIRequest;
import com.stackmob.core.rest.ResponseToProcess;
import com.stackmob.sdkapi.SDKServiceProvider;
import com.stackmob.sdkapi.*;

import com.stackmob.core.InvalidSchemaException;
import com.stackmob.core.DatastoreException;

import com.stackmob.sdkapi.LoggerService;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

// Added JSON parsing to handle JSON posted in the body
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class CreateVerifiedUser implements CustomCodeMethod {

  @Override
  public String getMethodName() {
    return "create_verified_user";
  }
    
    
  @Override
  public List<String> getParams() {
    return Arrays.asList("username","password","uuid");
  }  
    

  @Override
  public ResponseToProcess execute(ProcessedAPIRequest request, SDKServiceProvider serviceProvider) {

    String username = "";
    String password = "";
    String uuid = "";
    
    LoggerService logger = serviceProvider.getLoggerService(com.stackmob.example.CreateVerifiedUser.class);
    //Log the JSON object passed to the StackMob Logs
    logger.debug(request.getBody());

    // I'll be using these maps to print messages to console as feedback to the operation
    Map<String, SMValue> feedback = new HashMap<String, SMValue>();
    Map<String, String> errMap = new HashMap<String, String>();

    JSONParser parser = new JSONParser();
    try {
      Object obj = parser.parse(request.getBody());
      JSONObject jsonObject = (JSONObject) obj;

      //We use the username passed to query the StackMob datastore
      //and retrieve the user's name and email address
      username = (String) jsonObject.get("username");
      password = (String) jsonObject.get("password");
      uuid = (String) jsonObject.get("uuid");
    } catch (ParseException e) {
      return Util.internalErrorResponse("parse_exception", e, errMap);  // error 500
    }


    if (Util.hasNulls(username, password, uuid)){
      return Util.badRequestResponse(errMap);
    }

    // get the StackMob datastore service and assemble the query
    DataService dataService = serviceProvider.getDataService();

    // build a query
    List<SMCondition> query = new ArrayList<SMCondition>();
    query.add(new SMEquals("username", new SMString(username)));
    query.add(new SMEquals("uuid", new SMString(uuid)));

    List<SMObject> result;

    try {
      // return results from unverified user query
      result = dataService.readObjects("user_unverified", query);
      if (result != null && result.size() == 1) {

        SMObject newUserObject;

        try {
          // Create new User
          Map<String, SMValue> objMap = new HashMap<String, SMValue>();
          objMap.put("username", new SMString(username));
          objMap.put("password", new SMString(password));
          newUserObject = dataService.createObject("user", new SMObject(objMap));

          feedback.put("user_created", new SMString(newUserObject.toString()) );

        } catch (InvalidSchemaException e) {
          return Util.internalErrorResponse("invalid_schema", e, errMap);  // error 500 // http 500 - internal server error
        } catch (DatastoreException e) {
          return Util.internalErrorResponse("datastore_exception", e, errMap);  // http 500 - internal server error
        } catch(Exception e) {
          return Util.internalErrorResponse("unknown_exception", e, errMap);  // http 500 - internal server error
        }
      }
    } catch (InvalidSchemaException e) {
      return Util.internalErrorResponse("invalid_schema", e, errMap);  // error 500 // http 500 - internal server error
    } catch (DatastoreException e) {
      return Util.internalErrorResponse("datastore_exception", e, errMap);  // http 500 - internal server error
    } catch(Exception e) {
      return Util.internalErrorResponse("unknown_exception", e, errMap);  // http 500 - internal server error
    }

    return new ResponseToProcess(HttpURLConnection.HTTP_OK, feedback);
  }
}
