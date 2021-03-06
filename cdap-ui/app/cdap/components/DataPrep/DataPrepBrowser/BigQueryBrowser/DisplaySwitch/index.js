/*
 * Copyright © 2018 Cask Data, Inc.
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

import React from 'react';
import PropTypes from 'prop-types';
import DatasetList from 'components/DataPrep/DataPrepBrowser/BigQueryBrowser/DatasetList';
import TableList from 'components/DataPrep/DataPrepBrowser/BigQueryBrowser/TableList';
import { connect } from 'react-redux';

const DisplaySwitchView = ({ datasetId, onWorkspaceCreate }) => {
  return datasetId ? (
    <TableList enableRouting={false} onWorkspaceCreate={onWorkspaceCreate} />
  ) : (
    <DatasetList enableRouting={false} />
  );
};

DisplaySwitchView.propTypes = {
  datasetId: PropTypes.string,
  onWorkspaceCreate: PropTypes.func,
};

const mapStateToProps = (state) => {
  return {
    datasetId: state.bigquery.datasetId,
  };
};

const DisplaySwitch = connect(mapStateToProps)(DisplaySwitchView);

export default DisplaySwitch;
