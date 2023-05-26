// Copyright (c) 2013 VMware, Inc. All Rights Reserved.
// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package gerrit_owners;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.Term;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

/** 'code_review_user'(-User) */
public class PRED_code_review_user_1 extends Predicate.P1 {

  private static final PRED_code_review_user_check CODE_REVIEW_USER_CHECK =
      new PRED_code_review_user_check();
  private static final PRED_code_review_user_empty CODE_REVIEW_USER_EMPTY =
      new PRED_code_review_user_empty();
  private static final PRED_code_review_user_next CODE_REVIEW_USER_NEXT =
      new PRED_code_review_user_next();

  public PRED_code_review_user_1(Term a1, Operation n) {
    this.arg1 = a1;
    this.cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.cont = cont;
    engine.setB0();

    ChangeData cd = StoredValues.CHANGE_DATA.get(engine);
    Optional<LabelValue> codeReviewMaxValue =
        Optional.ofNullable(cd.getLabelTypes().byLabel(LabelId.CODE_REVIEW)).map(LabelType::getMax);

    Iterator<Account.Id> approvalsAccounts =
        cd.currentApprovals().stream()
            .filter(a -> LabelId.CODE_REVIEW.equalsIgnoreCase(a.labelId().get()))
            .filter(a -> codeReviewMaxValue.filter(val -> val.getValue() == a.value()).isPresent())
            .map(a -> a.accountId())
            .collect(Collectors.toList())
            .iterator();

    engine.r1 = arg1;
    engine.r2 = new JavaObjectTerm(approvalsAccounts);

    return engine.jtry2(CODE_REVIEW_USER_CHECK, CODE_REVIEW_USER_NEXT);
  }

  private static class PRED_code_review_user_check extends Operation {

    @Override
    public Operation exec(Prolog engine) throws PrologException {
      Term a1 = engine.r1;
      Term a2 = engine.r2;

      @SuppressWarnings("unchecked")
      Iterator<Account.Id> iter = (Iterator<Account.Id>) ((JavaObjectTerm) a2).object();
      while (iter.hasNext()) {
        Account.Id accountId = iter.next();
        IntegerTerm accountIdTerm = new IntegerTerm(accountId.get());
        if (!a1.unify(accountIdTerm, engine.trail)) {
          continue;
        }
        return engine.cont;
      }
      return engine.fail();
    }
  }

  private static class PRED_code_review_user_next extends Operation {

    @Override
    public Operation exec(Prolog engine) throws PrologException {
      return engine.trust(CODE_REVIEW_USER_EMPTY);
    }
  }

  private static class PRED_code_review_user_empty extends Operation {

    @Override
    public Operation exec(Prolog engine) throws PrologException {
      Term a2 = engine.r2;

      @SuppressWarnings("unchecked")
      Iterator<Account.Id> iter = (Iterator<Account.Id>) ((JavaObjectTerm) a2).object();
      if (!iter.hasNext()) {
        return engine.fail();
      }

      return engine.jtry2(CODE_REVIEW_USER_CHECK, CODE_REVIEW_USER_NEXT);
    }
  }
}
