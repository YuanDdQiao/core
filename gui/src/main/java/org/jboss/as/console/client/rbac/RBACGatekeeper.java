package org.jboss.as.console.client.rbac;

import com.google.gwt.core.client.Scheduler;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.proxy.Gatekeeper;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import org.jboss.as.console.client.plugins.AccessControlRegistry;
import org.jboss.ballroom.client.rbac.AuthorisationDecision;
import org.jboss.ballroom.client.rbac.SecurityContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Heiko Braun
 */
@Singleton
public class RBACGatekeeper implements Gatekeeper {

    private final AccessControlRegistry accessControlMetaData;
    private final PlaceManager placemanager;
    private final SecurityFramework securityFramework;

    @Inject
    public RBACGatekeeper(
            final AccessControlRegistry accessControlMetaData,
            final PlaceManager placemanager,
            final SecurityFramework securityManager, EventBus eventBus) {

        this.accessControlMetaData = accessControlMetaData;
        this.placemanager = placemanager;
        this.securityFramework = securityManager;

    }

    @Override
    public boolean canReveal() {

        String token = placemanager.getCurrentPlaceRequest().getNameToken();

        // important: without this the UnauthorisedPresenter cannot successfully navigate back
        placemanager.updateHistory(placemanager.getCurrentPlaceRequest(), true);

        boolean outcome = false;

        if(securityFramework.hasContext(token))
        {
            SecurityContext securityContext = securityFramework.getSecurityContext(token);
            final AuthorisationDecision readPriviledge = securityContext.getReadPriviledge();


            // bootstrap operations
            boolean bootstrapRequirementsSatisfied = true;
            for(String op : accessControlMetaData.getOperations(token))
            {
                int idx = op.indexOf("#");
                AuthorisationDecision opPriv = securityContext.getOperationPriviledge(
                        op.substring(0, idx),
                        op.substring(idx + 1, op.length())
                );

                if(!opPriv.isGranted())
                {
                    bootstrapRequirementsSatisfied = false;
                    break;
                }
            }


            outcome = readPriviledge.isGranted() && bootstrapRequirementsSatisfied;

            //notify listeners (error messages, etc)
            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                @Override
                public void execute() {
                    AuthDecisionEvent.fire(placemanager, readPriviledge);
                }
            });
        }

        return outcome;
    }

}