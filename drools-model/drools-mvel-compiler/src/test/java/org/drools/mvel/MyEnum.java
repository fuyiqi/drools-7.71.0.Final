/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Borrowed from MVEL, under the ASL2.0 license.
 * 
 */

package org.drools.mvel;

public enum MyEnum {
  ALTERNATIVE("Alternative"),
  FULL_DOCUMENTATION("FullDocumentation");

  private final String value;

  MyEnum(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static MyEnum fromValue(String v) {
    for (MyEnum c : MyEnum.values()) {
      if (c.value.equals(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }
}
