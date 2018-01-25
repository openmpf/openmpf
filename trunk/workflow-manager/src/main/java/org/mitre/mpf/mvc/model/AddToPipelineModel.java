/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.mvc.model;

import java.util.ArrayList;
import java.util.List;

public class AddToPipelineModel {
  /*  var objectToSend = {
  type : 'task',
  itemsToAdd : addedActionsArray,
  name : name,
  description : description
};*/
  
  private String type;
  private String name;
  private String description;
  private List<String> itemsToAdd = new ArrayList<String>();
  
//  public AddToPipelineModel(String type, String name, String description/*,
//      List<String> itemsToAdd*/) {
//    this.type = type;
//    this.name = name;
//    this.description = description;
//    //this.itemsToAdd = itemsToAdd;
//  }
  
  public String getType() {
    return type;
  }
  public void setType(String type) {
    this.type = type;
  }
  
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }
  
  public List<String> getItemsToAdd() {
    return itemsToAdd;
  }
  public void setItemsToAdd(List<String> itemsToAdd) {
    this.itemsToAdd = itemsToAdd;
  }
}
