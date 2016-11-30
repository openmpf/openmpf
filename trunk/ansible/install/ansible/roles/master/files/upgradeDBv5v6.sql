 -- -----------------------------------------------------------------------------
 -- NOTICE                                                                     --
 --                                                                            --
 -- This software (or technical data) was produced for the U.S. Government     --
 -- under contract, and is subject to the Rights in Data-General Clause        --
 -- 52.227-14, Alt. IV (DEC 2007).                                             --
 --                                                                            --
 -- Copyright 2016 The MITRE Corporation. All Rights Reserved.                 --
 -- -----------------------------------------------------------------------------

 -- -----------------------------------------------------------------------------
 -- Copyright 2016 The MITRE Corporation                                       --
 --                                                                            --
 -- Licensed under the Apache License, Version 2.0 (the "License");            --
 -- you may not use this file except in compliance with the License.           --
 -- You may obtain a copy of the License at                                    --
 --                                                                            --
 --    http://www.apache.org/licenses/LICENSE-2.0                              --
 --                                                                            --
 -- Unless required by applicable law or agreed to in writing, software        --
 -- distributed under the License is distributed on an "AS IS" BASIS,          --
 -- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   --
 -- See the License for the specific language governing permissions and        --
 -- limitations under the License.                                             --
 -- -----------------------------------------------------------------------------

UPDATE mpf.user_user_roles SET user_id = user;

CREATE INDEX FKhxmdhadm01kwqo2lvyv8l8ho7 ON mpf.user_user_roles(user_id);

ALTER TABLE mpf.user_user_roles ADD CONSTRAINT FKhxmdhadm01kwqo2lvyv8l8ho7 FOREIGN KEY (user_id) REFERENCES mpf.user (id) ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE mpf.user_user_roles DROP FOREIGN KEY FK_bnae0sdue52rvakgk5d009fwy;

ALTER TABLE mpf.user_user_roles DROP COLUMN user;

UPDATE mpf.job_request SET priority = '4' WHERE priority IS NULL;
