
Welcome, new core developer! The core team appreciates the quality of your work, and enjoy working with you; we have therefore invited you to join us. Thank you for your numerous contributions to the project so far.

This document offers guidelines for your new role. First and foremost, you should familiarize yourself with the project’s Mission and Vision. When in doubt, always refer back here.

As a core team member, you gain the responsibility of shepherding other contributors through the review process; here are some guidelines.


## **All Contributors Are Treated The Same**

You now have the ability to push changes directly to the main branch, but should never do so; 

As a core contributor, you gain the ability to merge or approve other contributors’ pull requests. 



* Minor changes that are likely uncontroversial: merge as you see fit, no need to wait
* Major changes: 2 core devs (one of which may be the author of the PR) must sign off

There are no definitive criteria for what constitutes a “Major change” or “Minor change”. It may also change depending on what repository the contribution is in: for example, a change to a single device adapter is more likely to be considered minor than a change to MMDevice API. Developers are encouraged to ask each other what is okay to merge without review and what isn’t, in order to develop a better understanding of the boundary. 


## **Reviewing**


### **How to Conduct A Good Review**

_Always_ be kind to contributors. Much of Micro-Manager is volunteer work, for which we are tremendously grateful. Provide constructive criticism on ideas and implementations, and remind yourself of how it felt when your own work was being evaluated as a novice.

Micro-Manager strongly values mentorship in code review. New users often need more handholding, having little to no git experience. Repeat yourself liberally, and, if you don’t recognize a contributor, point them to our development guide, or other GitHub workflow tutorials around the web. Do not assume that they know how GitHub works (e.g., many don’t realize that adding a commit automatically updates a pull request). Gentle, polite, kind encouragement can make the difference between a new core developer and an abandoned pull request.

When reviewing, suggesting big picture changes to the pull request to the author is important to improve the quality of code. It is also important to try to provide as much positive feedback and encouragement as possible during the process. Encouragement is an important motivator for contributors.

Other suggestions may be _nitpicky_: spelling mistakes, formatting, etc. Do not ask contributors to make these changes, and instead make the changes by[ pushing to their branch](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/committing-changes-to-a-pull-request-branch-created-from-a-fork) or using GitHub’s[ suggestion feature](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/commenting-on-a-pull-request). (The latter is preferred because it gives the contributor a choice in whether to accept the changes.)

Please add a note to a pull request after you push new changes; GitHub does not send out notifications for these.


### **Merge Only Changes You Understand**

_Long-term maintainability_ is an important concern. Code doesn’t merely have to _work_, but should be _understood_ by multiple core developers. Changes will have to be made in the future, and the original contributor may have moved on.

Therefore, _do not merge a code change unless you understand it_. Ask for help freely: we have a long history of consulting community members, or even external developers, for added insight where needed, and see this as a great learning opportunity.

While we collectively “own” any patches (and bugs!) that become part of the code base, you are vouching for changes you merge. Please take that responsibility seriously.

In practice, if you are the second core developer reviewing and approving a given pull request, you typically merge it in the wake of your approval. What are the exceptions to this process? If the pull request has been particularly controversial or the subject of much debate (e.g., involving API changes), then you would want to wait a few days before merging. This waiting time gives others a chance to speak up in case they are not fine with the current state of the pull request. Another exceptional situation is one where the first approving review happened a long time ago and many changes have taken place in the meantime.


## **Closing issues and pull requests**

Sometimes, an issue must be closed that was not fully resolved. This can be for a number of reasons:



* the person behind the original post has not responded to calls for clarification, and none of the core developers have been able to reproduce their issue;
* fixing the issue is difficult, and it is deemed too niche a use case to devote sustained effort or prioritize over other issues; or
* the use case or feature request is something that core developers feel does not belong in Micro-Manager,

among others. Similarly, pull requests sometimes need to be closed without merging, because:



* the pull request implements a niche feature that we consider not worth the added maintenance burden;
* the pull request implements a useful feature, but requires significant effort to bring up to Micro-Manager’s standards, and the original contributor has moved on, and no other developer can be found to make the necessary changes; or
* the pull request makes changes that do not align with our Mission and Vision

All these may be valid reasons for closing, but we must be wary not to alienate contributors by closing an issue or pull request without an explanation. When closing, your message should:



* explain clearly how the decision was made to close. This is particularly important when the decision was made in a community meeting, which does not have as visible a record as the comments thread on the issue itself;
* thank the contributor(s) for their work; and
* provide a clear path for the contributor or anyone else to appeal the decision.

These points help ensure that all contributors feel welcome and empowered to keep contributing, regardless of the outcome of past contributions.
