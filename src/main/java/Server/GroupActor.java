package Server;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import akka.actor.ActorSystem;
import akka.routing.ActorRefRoutee;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Router;
import Messages.Message.*;

public class GroupActor extends AbstractActor
{
    //Map of username-previledge. Includes every user that is in the group with his previledge.
    HashMap<String, Previledges> previledgesInformation = new HashMap<String, Previledges>();
    //Map of mutedusername-mutedtime. Includes every user that is in the group and muted
    //with his time of mute
    HashMap<String, Integer> mutedTimesInformation = new HashMap<String, Integer>();

    Router router;
    String groupName;


    public GroupActor(String groupName)
    {//Group actor constructor. Init group name and creates a new router
        this.groupName = groupName;
        router = new Router(new BroadcastRoutingLogic());
    }


    @Override
    public Receive createReceive()
    {//Handle incoming messages of the GroupActor's mailbox
        return receiveBuilder()
                .match(CreateGroupRequestMessage.class, crtGrpMsg ->
                {//Get a create group message from the server and add user to the group as admin
                    getContext().watch(sender());
                    router = router.addRoutee(new ActorRefRoutee(sender()));
                    previledgesInformation.put(crtGrpMsg.userName,Previledges.ADMIN);
                })
                .match(GroupMessage.class, grpMsg ->
                {//Get a group message from the server and broadcast it to the group's users
                 // if the sender is in the group and not muted
                    if(isUserInGroup(grpMsg.userName, sender()))
                        if(!mutedTimesInformation.containsKey(grpMsg.userName))
                            broadcastMessage(grpMsg.userName, grpMsg.groupName, grpMsg.text, false);
                        else// todo: check if user not in group handle
                        {//send info message to the muted sender
                            OtherMessage msg = new OtherMessage();
                            msg.text = "You are muted for "+ mutedTimesInformation.get(grpMsg.userName)+ " in " + groupName + "!";
                            sender().tell(msg, ActorRef.noSender());
                        }
                })
                .match(GroupFileMessage.class, grpfMsg ->
                {//Get a group message from the server and broadcast it to the group's users
                 // if the sender is in the group and not muted
                    if(previledgesInformation.containsKey(grpfMsg.userName))
                    {
                        if(!mutedTimesInformation.containsKey(grpfMsg.userName))
                            router.route(grpfMsg, self());
                        else
                        {
                            OtherMessage msg = new OtherMessage();
                            msg.text = "You are muted for "+ mutedTimesInformation.get(grpfMsg.userName)+ " in " + groupName + "!";
                            sender().tell(msg, ActorRef.noSender());
                        }
                    }
                    else
                    {
                        OtherMessage msg = new OtherMessage();
                        msg.text = "You are not part of "+ groupName + "!";
                        sender().tell(msg, self());
                    }
                })
                .match(InviteToGroupMessage.class, invMsg ->
                {//Get an invitation message, if user is in group and admin/coadmin
                 //send an ask group invitation message to the sender
                   if(isUserInGroup(invMsg.userName, sender()))
                   {
                        if(isUserAdminOrCoAdmin(invMsg.userName, sender()))
                        {
                            AskGroupInvMessage msg = new AskGroupInvMessage();
                            msg.groupName = groupName;
                            msg.target = invMsg.targetName;
                            msg.userName = invMsg.userName;
                            msg.targetActor = invMsg.targetActor;
                            msg.userActor = sender();
                            sender().tell(msg, self());
                        }
                   }
                })
                .match(AddUserToGroupMessage.class, addMsg ->
                {//Get add user to group message from inviter user
                 //after target user answered Yes to the group invitation message
                    getContext().watch(addMsg.userToAddActor);
                    router = router.addRoutee(new ActorRefRoutee(addMsg.userToAddActor));
                    previledgesInformation.put(addMsg.userToAdd, Previledges.USER);
                    PrivateUserTextMessage msg = new PrivateUserTextMessage();
                    msg.text = "Welcome to " + groupName + "!";
                    msg.sender = addMsg.sender;
                    addMsg.userToAddActor.tell(msg, self());
                    broadcastMessage(addMsg.userToAdd, groupName, addMsg.userToAdd + " has joined", true);
                })
                .match(RemoveFromGroupMessage.class, rmvMsg ->
                {//Get remove user from group message from server
                 //if user, target in group and user is coadmin/admin
                 //remove target user from group, broadcast relevant message
                 //and send relevant message to the sender
                    if(isUserInGroup(rmvMsg.userName, sender()) && isUserInGroup(rmvMsg.targetName, sender()))
                    {
                        if(isUserAdminOrCoAdmin(rmvMsg.userName, sender()))
                        {
                            previledgesInformation.remove(rmvMsg.targetName);
                            router = router.removeRoutee(rmvMsg.targetActor);
                            broadcastMessage(rmvMsg.userName, groupName, rmvMsg.targetName + " was removed", false);
                            sender().tell(rmvMsg, ActorRef.noSender());
                        }
                    }
                })
                .match(LeaveGroupRequestMessage.class, lvMsg ->
                {//Get leave group message from server
                    //if user is in group and user is not admin
                    //remove user from group, broadcast relevant message
                    //and send relevant message to the sender
                    //if the user is admin close the group
                    if(isUserInGroup(lvMsg.userName, sender()))
                    {
                        broadcastMessage(lvMsg.userName, groupName, lvMsg.userName + " has left " + groupName + "!", true);
                        CloseGroupMessage msg = new CloseGroupMessage();
                        msg.groupName = groupName;

                        if(previledgesInformation.get(lvMsg.userName) == Previledges.ADMIN)
                        {
                            broadcastMessage(lvMsg.userName, groupName, lvMsg.userName + " admin has closed " + groupName + "!", true);
                            router.route(msg, self());//send close msg to all clients in group
                            getContext().parent().tell(msg, self());
                        }
                        else
                        {//single client (not an admin)
                            sender().tell(msg, ActorRef.noSender());
                        }
                    }
                })
                .match(CloseGroupApproveMessage.class, clsMsg -> {
                {//Get close group approve message and remove sender from the group
                    previledgesInformation.remove(clsMsg.userName);
                    router = router.removeRoutee(sender());
                }
                })
                .match(AddCoadminGroupMessage.class, addCoMsg ->
                {//Get coadmin add to group message
                 //if the user and target are in the group and the user is admin/coadmin
                 //add the target to the group, broadcast relevant message
                 //and send to the target relevant message
                    if(isUserInGroup(addCoMsg.userToAdd, sender()) && isUserInGroup(addCoMsg.userName, sender()))
                    {
                        if(isUserAdminOrCoAdmin(addCoMsg.userName, sender()))
                        {
                            previledgesInformation.put(addCoMsg.userToAdd, Previledges.COADMIN);
                            OtherMessage msg = new OtherMessage();
                            msg.text = "You have been promoted to co-admin in " + addCoMsg.groupName + "!";
                            addCoMsg.userToAddActor.tell(msg, self());
                        }
                    }
                })
                .match(RemoveCoadminGroupMessage.class, rmvCoMsg ->
                {//Get coadmin remove from group message
                    //if the user and target are in the group and the user is admin/coadmin
                    //remove the target from the group, broadcast relevant message
                    //and send to the target relevant message
                    if(isUserInGroup(rmvCoMsg.userToRemove, sender()) && isUserInGroup(rmvCoMsg.userName, sender()))
                    {
                        if(isUserAdminOrCoAdmin(rmvCoMsg.userName, sender()))
                        {
                            previledgesInformation.put(rmvCoMsg.userToRemove, Previledges.USER);
                            OtherMessage msg = new OtherMessage();
                            msg.text = "You have been demoted to user in " + rmvCoMsg.groupName + "!";
                            rmvCoMsg.userToRemoveActor.tell(msg, self());
                        }
                    }
                })
                .match(MuteGroupMessage.class, mtMsg ->
                {//Get mute group message
                    //if the user is in the group and the user is admin/coadmin
                    //add the target to muted users map
                    //change user's previledge to muted
                    //mute the target in the group to the given time, send him a relevant message
                    //after the time has passed unmute the target and send relevant message
                    if(isUserInGroup(mtMsg.userName, sender()))
                    {
                        if(isUserAdminOrCoAdmin(mtMsg.userName, sender()))
                        {
                            if(!mutedTimesInformation.containsKey(mtMsg.target))
                            {
                                SendMuteMessage mmsg = new SendMuteMessage();
                                mmsg.groupName = groupName;
                                mmsg.target = mtMsg.target;
                                mmsg.userName = mtMsg.userName;
                                mmsg.targetActor = mtMsg.targetActor;
                                mmsg.time = mtMsg.time;
                                mmsg.userActor = sender();
                                mutedTimesInformation.put(mtMsg.target, mtMsg.time);
                                previledgesInformation.put(mtMsg.target, Previledges.MUTED);
                                sender().tell(mmsg, self());

                                SendUnMuteMessage ummsg = new SendUnMuteMessage();
                                ummsg.groupName = groupName;
                                ummsg.target = mtMsg.target;
                                ummsg.userName = mtMsg.userName;
                                ummsg.targetActor = mtMsg.targetActor;
                                ummsg.timeReson = true;
                                ummsg.userActor = sender();
                                ActorSystem system = getContext().getSystem();
                                system.scheduler().scheduleOnce(Duration.ofSeconds(mtMsg.time), sender(),ummsg, system.dispatcher(),self());
                            }
                            else
                            {
                                OtherMessage msg = new OtherMessage();
                                msg.text = mtMsg.target + " is already muted!";
                                sender().tell(msg, ActorRef.noSender());
                            }
                        }
                    }
                })
                .match(UnMuteGroupApproveMessage.class, unMtMsg ->
                {//Get unmute message, remove target from muted users map
                 //change user's previledge back to user
                    mutedTimesInformation.remove(unMtMsg.userName);
                    previledgesInformation.put(unMtMsg.userName, Previledges.USER);
                })
                .match(UnMuteGroupMessage.class, unMtMsg ->
                {//Get unmute group message
                    //if the user is in the group and the user is admin/coadmin
                    //remove the target from muted users map
                    //change user's previledge to muted
                    //unmute the target in the group, send him a relevant message
                    if(isUserInGroup(unMtMsg.userName, sender()))
                    {
                        if(isUserAdminOrCoAdmin(unMtMsg.userName, sender()))
                        {
                            if(mutedTimesInformation.containsKey(unMtMsg.target)) {
                                SendUnMuteMessage ummsg = new SendUnMuteMessage();
                                ummsg.groupName = groupName;
                                ummsg.target = unMtMsg.target;
                                ummsg.userName = unMtMsg.userName;
                                ummsg.targetActor = unMtMsg.targetActor;
                                ummsg.timeReson = false;
                                ummsg.userActor = sender();
                                sender().tell(ummsg, self());
                                mutedTimesInformation.remove(unMtMsg.target);
                                previledgesInformation.put(unMtMsg.target, Previledges.USER);
                            }
                            else
                            {//The target is already muted
                                OtherMessage msg = new OtherMessage();
                                msg.text = unMtMsg.target + " is not muted!";
                                sender().tell(msg, ActorRef.noSender());
                            }
                        }
                    }
                })
                .match(OtherMessage.class, msg ->
                {//Get other message- print it
                  System.out.println(msg.text);
                  })
                .build();
            }

    public boolean isUserInGroup (String userName, ActorRef userActor)
    {//Check if a given user is in the current group
     //if he is - return true else send an info message to the user and return false
        if(previledgesInformation.containsKey(userName))
            return true;

        OtherMessage msg = new OtherMessage();
        msg.text = userName + " is not in " + groupName + "!";
        userActor.tell(msg, self());
        return false;
    }

    public boolean isUserAdminOrCoAdmin (String userName, ActorRef userActor)
    {//Check if a given user is an admin or a coadmin in the current group
     //if he is - return true else send an info message to the user and return false
        Previledges p = previledgesInformation.get(userName);
        if (p == Previledges.ADMIN || p == Previledges.COADMIN)
            return true;

        OtherMessage msg = new OtherMessage();
        msg.text = "You are neither an admin nor a co-admin of " + groupName + "!";
        userActor.tell(msg, self());
        return false;
    }

    private void broadcastMessage(String userName, String groupName, String text, boolean joinBroadcast)
    {//Broadcast a message in group format to all of the users in the group
    // joinBroadcast flag represents if the message will be sent to the sender
        BroadcastTextMessage msg = new BroadcastTextMessage();
        msg.userName = userName;
        msg.groupName = groupName;
        msg.joinBroadcast = joinBroadcast;
        msg.text = "[" + LocalTime.now().toString() + "]" +"[" + msg.groupName + "]" +"[" + msg.userName + "]" + text;
        router.route(msg, self());
    }



}
