/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package gerrit_owners;

import com.vmware.gerrit.owners.OwnersStoredValues;
import com.vmware.gerrit.owners.common.PathOwners;
import com.vmware.gerrit.owners.common.Matcher;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import java.util.Iterator;

/**
 * 'owner_path'(-Path)
 */
public class PRED_matcher_path_1 extends Predicate.P1 {

  private static final PRED_owner_path_check OWNER_PATH_CHECK = new PRED_owner_path_check();
  private static final PRED_owner_path_empty OWNER_PATH_EMPTY = new PRED_owner_path_empty();
  private static final PRED_owner_path_next OWNER_PATH_NEXT = new PRED_owner_path_next();

  public PRED_matcher_path_1(Term a1, Operation n) {
    this.arg1 = a1;
    this.cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.cont = cont;
    engine.setB0();

    PathOwners owners = OwnersStoredValues.PATH_OWNERS.get(engine);
    engine.r1 = arg1;
    // must only iterate over the paths for which there are actual files so we need an extra loop
    engine.r2 = new JavaObjectTerm(owners.getMatches().values().iterator());
    return engine.jtry2(OWNER_PATH_CHECK, OWNER_PATH_NEXT);
  }

  private static class PRED_owner_path_check extends Operation {

    @Override
    public Operation exec(Prolog engine) throws PrologException {
      Term a1 = engine.r1;
      Term a2 = engine.r2;

      @SuppressWarnings("unchecked")
      Iterator<Matcher> iter = (Iterator<Matcher>) ((JavaObjectTerm) a2).object();
      while (iter.hasNext()) {
        Matcher matcher = iter.next();

        SymbolTerm pathTerm = SymbolTerm.create(matcher.getPath());
        if (!a1.unify(pathTerm, engine.trail)) {
          continue;
        }

        return engine.cont;
      }
      return engine.fail();
    }
  }

  private static class PRED_owner_path_next extends Operation {

    @Override
    public Operation exec(Prolog engine) throws PrologException {
      return engine.trust(OWNER_PATH_EMPTY);
    }
  }

  private static class PRED_owner_path_empty extends Operation {

    @Override
    public Operation exec(Prolog engine) throws PrologException {
      Term a2 = engine.r2;

      @SuppressWarnings("unchecked")
      Iterator<String> iter = (Iterator<String>) ((JavaObjectTerm) a2).object();
      if (!iter.hasNext()) {
        return engine.fail();
      }

      return engine.jtry2(OWNER_PATH_CHECK, OWNER_PATH_NEXT);
    }
  }

}
