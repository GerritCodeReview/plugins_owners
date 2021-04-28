## Attention-Set

The owners-autoassign plugin allows to customize the selection of owners
that need to be added to the attention-set.
By default, Gerrit adds all reviewers to the attention-set, which could
not be ideal when the list of owners automatically assigned could be
quite long, due to the hierarchy of the OWNERS files in the parent
directories.

The `owners-api.jar` libModule included in the owners' plugin project contains
a generic interface that can be used to customize Gerrit's default
attention-set behaviour.

## owner-api setup

Copy the `owners-api.jar` libModule into the $GERRIT_SITE/lib directory
and add the following entry to `gerrit.config`:

```
[gerrit]
  installModule = com.googlesource.gerrit.owners.api.OwnersApiModule
```

## Customization of the attention-set selection

The OwnersAttentionSet API, contained in the owners-api.jar libModule,
provides the following interface:

```
public interface OwnersAttentionSet {

  Collection<Account.Id> addToAttentionSet(ChangeInfo changeInfo, Collection<Account.Id> owners);
}
```

Any other plugin, or script, can implement the interface and provide
an alternative implementation of the Gerrit's default mechanism.

Example: select two random owners and add to the attention set by adding the
following script as $GERRIT_SITE/plugins/owners-attentionset-1.0.groovy.

```
import com.google.inject.*
import com.google.gerrit.common.*
import com.google.gerrit.entities.*
import com.google.gerrit.extensions.common.*
import com.google.gerrit.extensions.registration.*
import com.google.common.flogger.FluentLogger
import com.vmware.gerrit.owners.api.*
import java.util.*

@Singleton
class MyAttentionSet implements OwnersAttentionSet {
  def logger = FluentLogger.forEnclosingClass()
  def numAttentionSet = 3

  Collection<Account.Id> addToAttentionSet(ChangeInfo changeInfo, Collection<Account.Id> owners) {
    if (owners.size() <= numAttentionSet) {
      logger.atInfo().log("Adding all owners %s to attention set", owners)
      return owners
    }

    def shuffledOwners = Collections.shuffle owners
    ownersWithAttentionSet = shuffledOwners.subList(0,numAttentionSet)
    logger.atInfo().log("Adding new owners %s to attention set", ownersWithAttentionSet)
    return ownersWithAttentionSet
  }
}

class MyAttentionSetModule extends AbstractModule {

  protected void configure() {
    DynamicItem.bind(binder(), OwnersAttentionSet.class)
        .to(MyAttentionSet.class)
        .in(Scopes.SINGLETON)
  }
}

modules = [ MyAttentionSetModule.class ]
```

**NOTE**: Install the [groovy-provider plugin](https://gerrit.googlesource.com/plugins/scripting/groovy-provider/)
for enabling Gerrit to load Groovy scripts as plugins.