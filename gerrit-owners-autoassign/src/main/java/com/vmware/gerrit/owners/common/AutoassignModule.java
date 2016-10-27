package com.vmware.gerrit.owners.common;

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class AutoassignModule extends AbstractModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
        .to(GitRefListener.class);

    install(new FactoryModuleBuilder()
    .implement(RequestContext.class, RunAsContext.class)
    .build(RunAsContext.Factory.class));
  }
}
