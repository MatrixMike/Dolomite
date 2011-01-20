/*
 * Copyright 2009 Google Inc.
 * Copyright 2011 Guillaume Gautreau
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ghusse.dolomite.core;

import java.util.HashMap;
import java.util.Map;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.ScriptElement;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A JSONP request that is waiting for a response. The request can be canceled.
 *
 * @param <T> the type of the response object.
 */
public class SignedJsonpRequest<T> {

  /**
   * A global JS variable that holds the next index to use.
   */
  private static final String CALLBACKS_COUNTER_NAME = "__gwt_jsonp_counter__";

  /**
   * A global JS object that contains callbacks of pending requests.
   */
  private static final String CALLBACKS_NAME = "__gwt_jsonp__";
  private static final JavaScriptObject CALLBACKS = getOrCreateCallbacksObject();

  /**
   * Returns the next ID to use, incrementing the global counter.
   */
  private static native int getAndIncrementCallbackCounter() /*-{
    var name = @com.ghusse.dolomite.core.SignedJsonpRequest::CALLBACKS_NAME;
    var ctr = @com.ghusse.dolomite.core.SignedJsonpRequest::CALLBACKS_COUNTER_NAME;
    return $wnd[name][ctr]++;
  }-*/;

  private static Node getHeadElement() {
    return Document.get().getElementsByTagName("head").getItem(0);
  }

  /**
   * Returns a global object to store callbacks of pending requests, creating
   * it if it doesn't exist.
   */
  private static native JavaScriptObject getOrCreateCallbacksObject() /*-{
    var name = @com.ghusse.dolomite.core.SignedJsonpRequest::CALLBACKS_NAME;
    if (!$wnd[name]) {
      $wnd[name] = new Object();
      $wnd[name]
          [@com.ghusse.dolomite.core.SignedJsonpRequest::CALLBACKS_COUNTER_NAME]
          = 0;
    }
    return $wnd[name];
  }-*/;

  private static String nextCallbackId() {
    return "I" + getAndIncrementCallbackCounter();
  }

  private final String callbackId;

  private final int timeout;

  private final AsyncCallback<T> callback;

  /**
   * Whether the result is expected to be an integer or not.
   */
  private final boolean expectInteger;

  private final String callbackParam;

  private final String failureCallbackParam;
  
  private final String secret;
  
  private final String signatureParam;

  /**
   * Timer which keeps track of timeouts.
   */
  private Timer timer;

  /**
   * Create a new JSONP request.
   *
   * @param callback The callback instance to notify when the response comes
   *          back
   * @param timeout Time in ms after which a {@link TimeoutException} will be
   *          thrown
   * @param expectInteger Should be true if T is {@link Integer}, false
   *          otherwise
   * @param callbackParam Name of the url param of the callback function name
   * @param failureCallbackParam Name of the url param containing the the
   *          failure callback function name, or null for no failure callback
   */
  SignedJsonpRequest(AsyncCallback<T> callback, int timeout, boolean expectInteger,
      String callbackParam, String failureCallbackParam, String signatureParam, String secret) {
    callbackId = nextCallbackId();
    this.callback = callback;
    this.timeout = timeout;
    this.expectInteger = expectInteger;
    this.callbackParam = callbackParam;
    this.failureCallbackParam = failureCallbackParam;
    this.secret = secret;
    this.signatureParam = signatureParam;
  }

  /**
   * Cancels a pending request.
   */
  public void cancel() {
    timer.cancel();
    unload();
  }

  public AsyncCallback<T> getCallback() {
    return callback;
  }

  public int getTimeout() {
    return timeout;
  }

  @Override
  public String toString() {
    return "JsonpRequest(id=" + callbackId + ")";
  }

  // @VisibleForTesting
  String getCallbackId() {
    return callbackId;
  }

  /**
   * Sends a request using the JSONP mechanism.
   *
   * @param baseUri To be sent to the server.
   */
  void send(final String baseUri) {
    this.send(baseUri, new HashMap<String, String>());
  }
  
  void send(final String baseUri, Map<String, String> arguments){
  	registerCallbacks(CALLBACKS);
    
    
    String prefix = CALLBACKS_NAME + "." + callbackId;
    arguments.put(this.callbackParam, prefix + ".onSuccess");

    if (failureCallbackParam != null) {
      arguments.put(this.failureCallbackParam, prefix + ".onFailure");
    }
    
    UrlBuilder.sign(arguments, this.signatureParam, this.secret);
    
    final String uri = UrlBuilder.getUri(baseUri, arguments);
    
    ScriptElement script = Document.get().createScriptElement();
    script.setType("text/javascript");
    script.setId(callbackId);
    script.setSrc(uri.toString());
    timer = new Timer() {
      @Override
      public void run() {
        onFailure(new TimeoutException("Timeout while calling " + baseUri));
      }
    };
    timer.schedule(timeout);
    getHeadElement().appendChild(script);
  }

  private void onFailure(String message) {
    onFailure(new Exception(message));
  }

  private void onFailure(Throwable ex) {
    timer.cancel();
    try {
      if (callback != null) {
        callback.onFailure(ex);
      }
    } finally {
      unload();
    }
  }

  private void onSuccess(T data) {
    timer.cancel();
    try {
      if (callback != null) {
        callback.onSuccess(data);
      }
    } finally {
      unload();
    }
  }

  /**
   * Registers the callback methods that will be called when the JSONP response
   * comes back. 2 callbacks are created, one to return the value, and one to
   * notify a failure.
   *
   * @param callbacks the global JS object which stores callbacks
   */
  private native void registerCallbacks(JavaScriptObject callbacks) /*-{
    var self = this;
    var callback = new Object();
    callbacks[this.@com.ghusse.dolomite.core.SignedJsonpRequest::callbackId] = callback;
    callback.onSuccess = $entry(function(data) {
      // Box primitive types
      if (typeof data == 'boolean') {
        data = @java.lang.Boolean::new(Z)(data);
      } else if (typeof data == 'number') {
        if (self.@com.ghusse.dolomite.core.SignedJsonpRequest::expectInteger) {
          data = @java.lang.Integer::new(I)(data);
        } else {
          data = @java.lang.Double::new(D)(data);
        }
      }
      self.@com.ghusse.dolomite.core.SignedJsonpRequest::onSuccess(Ljava/lang/Object;)(data);
    });
    if (this.@com.ghusse.dolomite.core.SignedJsonpRequest::failureCallbackParam) {
      callback.onFailure = $entry(function(message) {
        self.@com.ghusse.dolomite.core.SignedJsonpRequest::onFailure(Ljava/lang/String;)(message);
      });
    }
  }-*/;

  /**
   * Cleans everything once the response has been received: deletes the script
   * tag and unregisters the callback.
   */
  private void unload() {
    /*
     * Some browsers (IE7) require the script tag to be deleted outside the
     * scope of the script itself. Therefore, we need to defer the delete
     * statement after the callback execution.
     */
  	Scheduler.get().scheduleDeferred(new ScheduledCommand(){
			@Override
			public void execute() {
				unregisterCallbacks(CALLBACKS);
        Node script = Document.get().getElementById(callbackId);
        if (script != null) {
          // The script may have already been deleted
          getHeadElement().removeChild(script);
        }
				
			}});
  }

  private native void unregisterCallbacks(JavaScriptObject callbacks) /*-{
    delete callbacks[this.@com.ghusse.dolomite.core.SignedJsonpRequest::callbackId];
  }-*/;
}
