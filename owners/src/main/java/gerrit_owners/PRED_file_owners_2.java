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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package gerrit_owners;

import static com.googlesource.gerrit.owners.common.StreamUtils.iteratorStream;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.rules.PrologEnvironment;
import com.google.gerrit.server.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PInstantiationException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import com.googlesource.gerrit.owners.OwnersStoredValues;
import com.googlesource.gerrit.owners.common.PathOwners;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Get the list of users owning this file Actually a variation of PRED_current_user/2 in gerrit main
 * module, sharing its cache.. This is really needed to avoid prolog consuming rules and RAM space
 *
 * <pre>
 *   gerrit_owners:file_owners(+FilePath, -UserListFormatted).
 * </pre>
 */
class PRED_file_owners_2 extends Predicate.P2 {

  PRED_file_owners_2(Term a1, Term a2, Operation n) {
    arg1 = a1;
    arg2 = a2;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();
    if (a1 instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }
    if (!a2.unify(createFormattedList(engine, a1), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }

  public String getFullNameFromId(Prolog engine, Account.Id accountId) {
    Map<Account.Id, IdentifiedUser> cache = StoredValues.USERS.get(engine);
    IdentifiedUser user = cache.get(accountId);
    if (user == null) {
      IdentifiedUser.GenericFactory userFactory = userFactory(engine);
      IdentifiedUser who = userFactory.create(accountId);
      cache.put(accountId, who);
      user = who;
    }
    Account account = user.asIdentifiedUser().state().account();
    String userName = account.fullName();
    return sanitizeAsSubmitLabel(userName);
  }

  public Term createFormattedList(Prolog engine, Term key) {
    String path = key.toString();
    PathOwners owners = OwnersStoredValues.PATH_OWNERS.get(engine);
    Set<String> ownersNames =
        iteratorStream(owners.getFileOwners().get(path).iterator())
            .map(id -> getFullNameFromId(engine, id))
            .collect(Collectors.toSet());
    String ownVerb = ownersNames.size() > 1 ? "-own-" : "-owns-";
    String userNames = ownersNames.stream().collect(Collectors.joining("-"));
    return SymbolTerm.create(userNames + ownVerb + sanitizeAsSubmitLabel(new File(path).getName()));
  }

  private String sanitizeAsSubmitLabel(String anyLabelPart) {
    return anyLabelPart.replaceAll("[\\s_\\.]+", "-");
  }

  private static IdentifiedUser.GenericFactory userFactory(Prolog engine) {
    return ((PrologEnvironment) engine.control).getArgs().getUserFactory();
  }
}
