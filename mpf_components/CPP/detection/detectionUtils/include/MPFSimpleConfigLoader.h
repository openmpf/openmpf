/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

#ifndef MPF_SIMPLE_CONFIG_LOADER_H_
#define MPF_SIMPLE_CONFIG_LOADER_H_

#include <QDir>
#include <QMap>
#include <QHash>
#include <QFile>
#include <QObject>
#include <QString>
#include <QStringList>

#include <string>
#include <map>

namespace MPF {
    namespace COMPONENT {

        int LoadConfig(const std::string &config_path, QHash <QString, QString> &parameters) {
            QFile file(QString::fromStdString(config_path));

            parameters.clear();

            if (!file.open(QIODevice::ReadOnly | QIODevice::Text)) {
                printf("ERROR: Config file not loaded.\n");
                return (-1);
            }

            QByteArray line = file.readLine();
            while (line.count() > 0) {
                QStringList list = QString(line).left(line.indexOf('#')).split(": ");
                if (list.count() == 2) {
                    parameters.insert(list[0].simplified(), list[1].simplified());
                }
                line = file.readLine();
            }
            file.close();
            return (0);
        };

    }
};

#endif  // MPF_SIMPLE_CONFIG_LOADER_H_
