/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var App = require('app');

App.TextDashboardWidgetView = App.DashboardWidgetView.extend({

  templateName: require('templates/main/dashboard/widgets/simple_text'),

  classNameBindings: ['isRed', 'isOrange', 'isGreen', 'isNA'],

  isRed: Em.computed.lteProperties('data', 'thresholdMin'),
  isOrange: Em.computed.and('!isGreen', '!isRed'),
  isGreen: Em.computed.gtProperties('data', 'thresholdMax'),

  isNA: function () {
    return this.get('data') === null;
  }.property('data'),

  hiddenInfo: []
});
