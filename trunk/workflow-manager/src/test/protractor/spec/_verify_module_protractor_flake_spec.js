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

/** this set of tests are really just to verify that the npm module protractor-flake is running correctly
 *  To run, run the following in a bash shell from the test directory (not the current directory):
 *    node_modules/.bin/protractor-flake --max-attempts=5 -- protractor/protractor.conf.js --specs='protractor/spec/_verify_module_protractor_flake_spec.js'
 *  Note that this should usually be commented out, or made into a xdescribe once the module is verified in
 *  an environment
 */
describe('Verify that the module protractor-flake works as expected (should x this once it is verified in each environment)', function() {

    /** this should always fail each run of the test, use xit to skip */
    //it('verify test, always fails', function() {
    //    expect( false ).toBeTruthy();
    //});
    //
    ///** this should succeed with (on average) 2 attempts, use xit to skip */
    //it('verify test, fails randomly, to test protractor-flake runner', function() {
    //   expect( Math.floor(Math.random() * 5) ).toBeTruthy();
    //});
    //
});