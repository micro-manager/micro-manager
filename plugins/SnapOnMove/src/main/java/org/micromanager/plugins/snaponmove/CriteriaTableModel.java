// Snap-on-Move Preview for Micro-Manager
//
// Author: Mark A. Tsuchida
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from this
// software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.micromanager.plugins.snaponmove;

import javax.swing.table.AbstractTableModel;

final class CriteriaTableModel extends AbstractTableModel {
   private static final int NUM_COLUMNS = 1;

   private final MainController controller_;

   CriteriaTableModel(MainController controller) {
      controller_ = controller;
   }

   @Override
   public int getRowCount() {
      return controller_.getChangeCriteria().size();
   }

   @Override
   public int getColumnCount() {
      return NUM_COLUMNS;
   }

   @Override
   public String getColumnName(int col) {
      // col should always be 0 as we have 1 column
      return "Movement Criteria";
   }

   @Override
   public Object getValueAt(int row, int col) {
      // col should always be 0 as we have 1 column
      return controller_.getChangeCriteria().get(row).toString();
   }
}
