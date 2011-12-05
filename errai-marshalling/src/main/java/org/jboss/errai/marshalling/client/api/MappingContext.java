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

package org.jboss.errai.marshalling.client.api;

/**
 * @author Mike Brock
 */
public interface MappingContext {
  public Class<? extends Marshaller> getMarshallerClass(String clazz);

  public void registerMarshaller(String clazzName, Class<? extends Marshaller> clazz);

  public boolean hasMarshaller(String clazzName);

  /**
   * Indicates whether or not the specified class can be marshalled, whether or not a definition exists.
   * @return boolean true if marshallable.
   */
  public boolean canMarshal(String cls);
}