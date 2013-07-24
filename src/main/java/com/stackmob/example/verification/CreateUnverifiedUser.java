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

import com.stackmob.sdkapi.http.HttpService;
import com.stackmob.sdkapi.http.request.HttpRequest;
import com.stackmob.sdkapi.http.request.GetRequest;
import com.stackmob.sdkapi.http.response.HttpResponse;
import com.stackmob.core.ServiceNotActivatedException;
import com.stackmob.sdkapi.http.exceptions.AccessDeniedException;
import com.stackmob.sdkapi.http.exceptions.TimeoutException;
import com.stackmob.core.InvalidSchemaException;
import com.stackmob.core.DatastoreException;

import java.net.MalformedURLException;
import com.stackmob.sdkapi.http.request.PostRequest;
import com.stackmob.sdkapi.http.Header;
import com.stackmob.sdkapi.LoggerService;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

// Added JSON parsing to handle JSON posted in the body
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.UUID;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

public class CreateUnverifiedUser implements CustomCodeMethod {

  @Override
  public String getMethodName() {
    return "create_unverified_user";
  }
    
    
  @Override
  public List<String> getParams() {
    return Arrays.asList("username","email");
  }  
    

  @Override
  public ResponseToProcess execute(ProcessedAPIRequest request, SDKServiceProvider serviceProvider) {
    String username = "";
    String email = "";

    // SendGrid Vars
    String API_USER = "";
    String API_KEY  = "";
    String subject = "New User Verification ";
    String text = "";
    String from = "sid@stackmob.com";
    String to = "";
    String toname = "";
    String body = "";
    String url = "";
    
    LoggerService logger = serviceProvider.getLoggerService(CreateUnverifiedUser.class);
    //Log the JSON object passed to the StackMob Logs
    logger.debug(request.getBody());

    // I'll be using these maps to print messages to console as feedback to the operation
    Map<String, SMValue> feedback = new HashMap<String, SMValue>();
    Map<String, String> errMap = new HashMap<String, String>();

    try {
      API_USER = serviceProvider.getConfigVarService().get("SENDGRID_USERNAME","sendgrid");
      API_KEY  = serviceProvider.getConfigVarService().get("SENDGRID_PASSWORD","sendgrid");
    } catch(Exception e) {
      return Util.internalErrorResponse("configvar_service_exception", e, errMap);  // error 500
    }

    JSONParser parser = new JSONParser();
    try {
      Object obj = parser.parse(request.getBody());
      JSONObject jsonObject = (JSONObject) obj;

      //We use the username passed to query the StackMob datastore
      //and retrieve the user's name and email address
      username = (String) jsonObject.get("username");
      email = (String) jsonObject.get("email");

    } catch (ParseException e) {
      return Util.internalErrorResponse("parse_exception", e, errMap);  // error 500
    }


    if (Util.hasNulls(username)){
      return Util.badRequestResponse(errMap);
    }

    // get the StackMob datastore service and assemble the query
    DataService dataService = serviceProvider.getDataService();

    SMObject newUserObject;
    SMObject newUserVerifyObject;
    String uuid = UUID.randomUUID().toString();

    try {
        // create new unverified user query
        Map<String, SMValue> objUserVerifyMap = new HashMap<String, SMValue>();
        objUserVerifyMap.put("username", new SMString(username));
        objUserVerifyMap.put("email", new SMString(email));
        objUserVerifyMap.put("uuid", new SMString(uuid));
        newUserObject = dataService.createObject("user_unverified", new SMObject(objUserVerifyMap));

        feedback.put("user_unverified", new SMString(newUserObject.toString()) );

        text = "Welcome to my App! <br/><br/>Please <a href='http://localhost:4567/#verify/"+  username + "/" + uuid +"'>Verify Your Account and Create a Password</a>";


    } catch (InvalidSchemaException e) {
      return Util.internalErrorResponse("invalid_schema", e, errMap);  // error 500 // http 500 - internal server error
    } catch (DatastoreException e) {
      return Util.internalErrorResponse("datastore_exception", e, errMap);  // http 500 - internal server error
    } catch(Exception e) {
      return Util.internalErrorResponse("unknown_exception", e, errMap);  // http 500 - internal server error
    }

    if (Util.hasNulls(subject)){
      return Util.badRequestResponse(errMap);   // http 400 bad response
    }

    //Encode any parameters that need encoding (i.e. subject, toname, text)
    try {
      subject = URLEncoder.encode(subject, "UTF-8");
      text = URLEncoder.encode(text, "UTF-8");
      toname = URLEncoder.encode(toname, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return Util.internalErrorResponse("unsupported_encoding_exception", e, errMap);  // http 500 - internal server error
    }
    
    String queryParams = "api_user=" + API_USER + "&api_key=" + API_KEY + "&to=" + email + "&toname=" + username + "&subject=" + subject + "&html=" + text + "&from=" + from;

    url =  "https://www.sendgrid.com/api/mail.send.json?" + queryParams;
 
    Header accept = new Header("Accept-Charset", "utf-8");
    Header content = new Header("Content-Type", "application/x-www-form-urlencoded");
    
    Set<Header> set = new HashSet();
    set.add(accept);
    set.add(content);

    try {  
      HttpService http = serviceProvider.getHttpService();
          
      PostRequest req = new PostRequest(url,set,body);
      HttpResponse resp = http.post(req);

      feedback.put("email_response", new SMString(resp.getBody()) );

    } catch(TimeoutException e) {
      return Util.internalErrorResponse("internal_error_response", e, errMap);  // http 500 - internal server error
    } catch(AccessDeniedException e) {
      return Util.internalErrorResponse("access_denied_exception", e, errMap);  // http 500 - internal server error
    } catch(MalformedURLException e) {
      return Util.internalErrorResponse("malformed_url_exception", e, errMap);  // http 500 - internal server error
    } catch(ServiceNotActivatedException e) {
      return Util.internalErrorResponse("service_not_activated_exception", e, errMap);  // http 500 - internal server error
    }

    return new ResponseToProcess(HttpURLConnection.HTTP_OK, feedback);
  }
}
