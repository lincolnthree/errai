/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.bus.client.api;

import org.jboss.errai.bus.client.framework.MessageBus;
import org.jboss.errai.bus.client.framework.SubscriptionEvent;

/**
 * Callback interface for receiving notifications when a subscription is
 * de-registered on the bus.
 *
 * @see MessageBus#addUnsubscribeListener(UnsubscribeListener)
 */
public interface UnsubscribeListener {

  /**
   * Called when a subscription is de-registered from the bus.
   *
   * @param event event describing the subscription cancellation.
   */
  public void onUnsubscribe(SubscriptionEvent event);
}
