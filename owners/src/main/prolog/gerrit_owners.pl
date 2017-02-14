:- package gerrit_owners.
'$init'.

:- public add_owner_approval/2.
:- public add_owner_approval/3.
:- public findall_match_file_user/1.

add_owner_approval(In, Out) :-
  owner_path(Path),
  \+ owner_approved(Path),
  Out = [label('Owner-Approval', need(_)) | In],
  !.

add_owner_approval(In, Out) :- In = Out.

add_owner_approval(Users, In, Out) :-
  owner_path(Path),
  \+ owner_approved(Users, Path),
  Out = [label('Owner-Approval', need(_)) | In],
  !.

add_owner_approval(_, In, Out) :- In = Out.

owner_approved(Path) :-
  owner(Path, User),
  gerrit:commit_label(label('Code-Review', 2), User),
  !.

owner_approved(Users, Path) :-
  owner(Path, User),
  member(User, Users),
  !.

member(X, [X|_]).
member(X, [_|L]) :- member(X, L).

% add extra label for every file F
findall_match_file_user(FileAndUser) :-
    matcher_path(F),
    findall(US,code_review_user(US),Approvers),
    gerrit_owners:matcher_needed(Approvers,F,FileAndUser).

code_review_user(U) :-
    gerrit:commit_label(label('Code-Review', 2), user(U)).

% this loops over all the paths and if for any
% we have some labels generated then add a single
% Owner-Approval need to block submit button
add_match_owner_approval(In,Out) :-
    matcher_path(P),
    findall(US,code_review_user(US),Approvers),
    matcher_needed(Approvers,P,W),
    \+ W == [],
    Out = [label('Owner-Approval', need(_)) | In], !.

add_match_owner_approval(In,Out) :- Out = In.

matcher_needed(Users,Path,W) :-
   findall(US,needed_review_user(Path,US),NSL),
   subtract(Users,NSL,Diff),
   % if Users - Needed is unchanged this means we are missing at
   % least one (!)
   Diff == Users,
   file_owners(Path,FormattedLabel),
   W = label(FormattedLabel, need(_)).

needed_review_user(Path,User) :-
    matcher_owner(Path,user(User)).

%%%%%%%%%%%%%%%
% utility functions
%%%%%%%%%%%%%%%

%
% convert a Var back to a sequence of solutions to be feed
% in prolog (test purpose).
%
enumerate([H|T],F) :- F = H ; enumerate(T,F).

%
% check if first arg is a member of second list arg
%
memberchk(X,[X|_]) :- !.
memberchk(X,[_|T]):- memberchk(X,T).

%
% subtract second list from first list giving result
% used to understand if ApprovedList-NeededList  is changed
% in this case we understand that at least one owner of the matching
% gave the ok
%
subtract([], _, []) :- !.
subtract([A|C], B, D) :-
    memberchk(A, B), !,
    subtract(C, B, D).
subtract([A|B], C, [A|D]) :-
    subtract(B, C, D).

append([],L,L).
append([H|T],L2,[H|L3])  :-  append(T,L2,L3).

prepend([],L,L).
prepend([X|T],L,[X|Result]):- prepend(T,L,Result).
prepend(X,L,[X|L]).

