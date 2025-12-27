---
title: Why a new programming language?
---

Temper is different from existing programming languages.

- It's designed for building libraries, not whole programs.
- It's designed, from the ground up, to translate to all the other
  programming languages.

Temper lets an individual or small group comprehensively solve a
technical problem across an organization or the open-source community.

## From an individual's perspective

The year is 2000, Sam is a developer.
She notices that a lot of people are writing code that takes strings
of HTML from untrusted sources and including that in HTML.

She studies HTML and how browsers work until she understands deeply
how to rewrite HTML strings to make them trustworthy: removing
tags like `<script>`, for example, to avoid XSS.

She writes an *HTML sanitizer* in Python.  It works well, and many
web-serves written in Python use it and become safer.

*Frustration*.  She's solved a problem but only for one small corner
of the ecosystem.  She carefully translates her code into C++, Java,
JavaScript, and PHP to increase its reach.

This level of work is heroic, but doable.

Late some stormy night, someone reports an actively exploited security
vulnerability.  Sam crawls out of bed.  She edits and carefully tests
the code in Python, the language she's most familiar with.
She releases a fix, pushes it to module repositories, and works with
framework maintainers to manage its release into code that bundles her
library.

She must now repeat that change for the corresponding libraries in
C++, Java, JavaScript, and PHP.  Sam must maintain a high level of
focus, despite the copy-pastey feel of the work.

Maybe she does this. And, maybe her n<sup>th</sup> fix is as high
quality as the first, (Yay!), despite the fact that she doesn't
regularly work in some of those languages.

After a night of sleep, she wakes up exhausted, but proud that her
efforts have made the world safer. She checks the news: "Trends
suggest next year 30% of new projects will use a cool new language:
*C#*", which Sam has yet to learn.

Sam might understandably be a tad discouraged.

Individual developers and small teams can't solve problems across the
ecosystem if they need this skillset:

<div id="job-req" style="padding: 1ex; margin: 4ex; transform: rotate(-5deg); font-family: Garamond; font-size: 125%; background-color: #eee; filter: drop-shadow(30px 10px 4px #444444); text-align: justify; border: 4px dotted #888">
  <h3 style="margin-top: 0; font-variant: small-caps">Wanted: Problem Fixer</h3>

  <p>Must deeply understand a complex problem.</p>

  <p>Must be able to carefully craft code in
  C++, Go, a JVM language, JavaScript, Lua, a .Net language,
  PHP, Python, Ruby, Rust, Swift ... and next year's new language.</p>

  <p>Must be willing to work for little extra recognition or
  compensation from people who rarely know they had the problem you
  solved.</p>

  <p style="margin-bottom: 0">Must be available on short notice to
  deal with zero-days even when doing so displaces work that gets you
  paid / promoted.</p>
</div>
<br>

The supply of heroes is limited so the open-source community can only
provide wide coverage and long term support for solutions to
a few tricky problems, or to problems that are widely understood.

It'd be nice if people who really understand a problem deeply could
have a big effect without having to be heroic.

Had Temper been around in 2000, Sam could have written her *HTML
sanitizer* once.  Temper translates to all the languages; had Sam
written her *HTML sanitizer* in Temper, she could have provided a
library to every programming language community.

And when Sam had to fix a zero-day vulnerability, she could fix it
in one place.

Temper won't solve the sometimes [thankless nature](https://xkcd.com/2347/)
of critical infrastructure work, but it can solve scaling problems to
lighten willing people's burden.

## From an organization's perspective

The same kind of pain that Sam, our open-source contributor, feels
affects whole engineering departments.

In 2010, Sam gets a job at *GiantTechCo*.  Her new job is to integrate
her work on HTML safety across their entire engineering department.
GiantTechCo has ten thousand developers working in a dozen programming
languages, including a home-grown HTML template language.

Sam works to integrate her libraries into *GiantTechCo*'s stack, but
she runs into an oddity.  *GiantTechCo* has a
`class User { public displayName: String; ... }`.  For almost all their
users, who created their accounts via *GiantTechCo.com*, the
display name is plain text.  But GiantTechCo acquired a social network,
*OurSpace.com*, and imported all their users' display names.
Sam notices that many OurSpace users use HTML in their display names;
User `html I&lt;3<font color=red>Cats</font>!` expects their name
to display as <i>I<3<font color="red">Cats</font></i> and routinely
files bugs "You broke my heart, again :(" when some piece of GiantTechCo
software displays *I&amp;lt;3&hellip;*.

Meetings ensue.  Sam and her colleagues at GiantTechCo brainstorm ways
to safely render names across their software stack without worsening
OurSpace users' experience.

> Sam: We need a way for any of our teams to sanitize display names.
>
> Alice: What about storing the HTML version of the name in the database?
> Then our *User* objects have a *displayNameHtml* field.
>
> Bob: We could but we also use display names in text messages and in
> multi-part body emails.  The people who work on those have needs
> around how different account names convert.
>
> Alice: If all our code was in one language, I'd just write a name
> class that knows how it converts in these different contexts.
>
> Sam: I wonder if starting with a content type for a priori trusted
> HTML and one for plain text is a place to start.  And my sanitizer
> could produce that type, and we can integrate it into Template
> rendering; not re-escape trusted HTML.
>
> Bob: Having a content type that spans many systems and that integrates
> with content composition systems like Templates sounds widely useful.
> Maybe that's a good candidate if we're going to translate a few
> type definitions into many languages.
>
> Alice: Playing devil's advocate: what about a micro-service that
> converts content strings of any type into any other type.
>
> Sam: Calling out to a service many times to fill a template wouldn't
> be super efficient.  Batching might help.
>
> Bob: Also, the app and web people need users to be able to write
> draft product reviews.  It's important for drafts to save locally
> when offline.  Not having to work around network failures would be
> nice there.  And we do need to sanitize drafts.  Users copy/paste HTML;
> they don't knowingly trust everything they put in a rich text editor.
>
> Sam: So I'll bite the bullet and make sure we have a way to represent
> content, make it safe, convert between them, and compose them across
> multiple languages.
>
> Bob: That sounds like a lot of work, so thank you.  And I like that
> in Python our type `assert`s will just work, and in Java, the type
> checker will help us keep track of which kinds of strings are which.
>
> Alice: Yeah.  It's like you're extending type-check guardrails
> across the gap between our services.

Her colleagues love Sam's idea, and commit to helping organize a
company-wide fix-it day to update the code that creates *User* objects
and content strings from databases and migrate code that uses *User*
objects.

Sam first has to come up with implementations of *ContentString* for
each language though.  Maybe her Java version looks like

```java
interface ContentString {
    TrustedHtmlContent toTrustedHtml();
    PlainContent toPlainContentLossy();
}

class PlainContent implements ContentString {
    final String plainText;
    ...
}
/** A string of content known a-priori */
class TrustedHtmlContent implements ContentString {
    final String htmlText;
    ...
    static TrustedHtmlContent fromUntrustedHtml(String html) ...
}
```

Sam translates type definitions into the many languages used within
GiantTechCo, and then works with her colleagues to fix the places that
create *User* objects from databases and migrate code that uses *User*
objects.

GiantTechCo acquires a company whose users love using custom emojis in
content.  Sam and her co-workers are praised because integrating
content with custom emojis would have been much harder if content
strings didn't know how to convert to other types.

When Sam leaves GiantTechCo, she's proud of her work helping people
develop more reliable software and making users safer.  But she also
feels frustrated that her contributions were limited by the need to
redo work across many programming languages.

With Temper, Sam could have shared common type definitions across all
the languages used within GiantTechCo.  Someone who recognizes common
problems and who supports other developers by crafting libraries can
have a much greater impact when they don't have to do work per
programming language.

## What if there was only one programming language?

Sam is frustrated because she solves problems by writing code, but
the number of programming languages is just too damn high.

Why not just make one programming language to replace all the
other languages?

That's been tried, as discussed below, but different people
use different tools for good reasons.

Temper's design assumes that:

- The number of languages used is not going to decrease.
- The ten most popular languages today will not be the
  most popular ten years from now.
- Future developers will not be able to produce reliable
  code in more languages on average than developers today.
- Despite this, different language communities need a way
  to share solutions to cross-cutting problems.

Temper is designed to enable new ways of sharing code in
complex, multi-language systems.

### We need different programming languages

Programming languages are just tools, and wise developers pick the
right tool for the job at hand.

<div style="margin: 1em 0 1em 3em" markdown="1"><figure id="partition" markdown="1">

  ![Cluster graph of technologies.&#10;The legend includes four&#10;types:&#10;&#10;Database&#10;Framework&#10;Language&#10;Platform&#10;&#10;At the cluster labeled "Data Science" including&#10;Hadoop, Scala, Torch/PyTorch, and Python.&#10;&#10;Python is close to the intersection with a cluster near the mid-left&#10;labeled "Services" which includes Ansible, AWS, Docker, ElasticSearch,&#10;Go, and PostgresSQL.&#10;&#10;At mid-right is a cluster labeled "Mobile" which includes Android, Kotlin,&#10;iOS, Java, Firebase, Objective-C, and Swift.&#10;&#10;Mid-bottom is a large cluster labeled "Web" which includes Express,&#10;HTML/CSS, JavaScript, jQuery, MongoDB, MySQL, Node.js, and TypeScript.&#10;&#10;Near the "Web" cluster at the bottom left is a small cluster labeled&#10;"Windows Native."  It includes ASP.Net, Microsoft Azure, Microsoft SQL&#10;Server, .Net and Windows.](./images/stackoverflow-tech-cluster-graph.png)

<figcaption><font name="FF Din">stack<b>overflow</b></font> Survey.  Blue labels are added.</figcaption></figure></div>

["How Technologies Are Connected"](https://insights.stackoverflow.com/survey/2020#correlated-technologies) notes:

> Technologies cluster together into related ecosystems that tend to
> be used by the same developers. This network graph demonstrates this
> tendency by showing which technologies are most highly correlated
> with each other.

An organization with a custom web application, a mobile app, and an
internal data science group probably has code in five to eight
different programming languages.  Most developers only master a handful
of those languages well enough to produce defensive code.

- Data scientists are going to use languages tuned for math that
  let them assemble a model step-by-step.
- App and web developers are going to use languages that are tuned
  for presenting user interfaces.
- Backend developers are going to use languages so that the programs
  they write are easy to maintain in a cloud production environment.
- High-throughput systems developers and widely used app developers
  are going to use systems languages that let them extract every bit
  of performance.

But there are many common problems that cut across these domains.
Problems, like Sam's content representation problem, that aren't about
whether the code is running on a mobile device, in a browser, or on a
backend.

Temper is the right tool for those.

### One language policies have been tried

Temper is not supposed to replace languages used to build weapons
systems but the US Department of Defense's (DoD) attempts are
instructive.

In 1976, [the DoD was worried about fragmentation](https://apps.dtic.mil/sti/citations/ADA028297):
too little sharing of tools and programming languages between projects:

> The lack of **programming language commonality** in DoD embedded computer applications may:
>
> - Require **duplication** in training and maintenance...
> - Minimize communication among practitioners and retard technology
>   transfer.
> - Result in support software being **project-unique** and tie
>   software maintenance to the **original developer**.
> - Diffuse expenditures for **support and maintenance** software so
>   only the most **primitive** software aids are developed, **but
>   repeatedly**.

A modest training budget can develop manuals, courses, and
certification processes to bring trainees up to speed on a few
languages, but not hundreds of languages.

If there are a small number of languages, many programmers will be
familiar with most even if only expert in a few, which makes tech
transfer easy.

With a programming language community of thousands sharing a few
languages, managers can reassign people as needed. Without, no-one
gets to take vacation or die.

The over-arching theme: amortizing a few common infrastructure
projects over many developers lets us have nice things.

Ten years later, [Warren Soong reflected](https://apps.dtic.mil/sti/citations/ADA251859),
on the DoD's efforts to create one language and get every project to
use it:

> The Ada programming language has been adopted, mandated and
> legislated for use by the Department of Defense for all software
> development where cost effective.

How successfully did the DoD deal with its commonality problem?
["A Survey of Computer Programming Languages (DoD Language Survey)"](https://apps.dtic.mil/sti/citations/ADA294001)
noted:

- From 1974 to 1995, languages used reduced from 450 to 37.

With a concerted effort, the DoD managed to reduce the number of
languages used by an order of magnitude. The survey also notes that the
DoD is unlikely to see another order of magnitude reduction: from 37
to 4. Further improvements are likely to come from improving
integration in multi-language systems:

> Even if only one language were used, software commonality,
> portability, and interoperability would be imperfect. &hellip; With
> modern programming languages and compilers, &hellip;, it is possible
> to produce applications with components written in different
> languages.


## Adventures in engineering management

How might a tool like Temper fit into a polyglot engineering organization?

Management structures like this can be very stable:

[](){ #management-silos }

<div style="margin: 1em 0 1em 3em" markdown="1"><figure markdown="1">

![Org structure where a CTO (Chief Technical Officer) has reporting to them a director of data science, a director of mobile, and a director of web; each director has different developers reporting to them](./images/org-structure-siloed.png)

<figcaption>Siloed engineering management</figcaption></figure></div>

Each director hires from a different candidate pool and manages
developers who work smoothly together because they share context:
programming languages, tools, and jargon.

Two developers from different teams likely have less shared context;
they're less able to recognize and work together on shared problems.

How might the CTO [above](#management-silos) help people like Sam?

Assuming some level of siloing is inevitable, they might create a
*common infrastructure team* whose charter is to support other
verticals by providing solutions to cross-cutting problems that work
with each vertical's preferred tools.

<div style="margin: 1em 0 1em 3em" markdown="1"><figure id="management-silos-plus-cross" markdown="1">

  ![Org structure as in the earlier figure but with a Director of common infrastructure whose team supports each of the other directors' teams](./images/org-structure-siloed-with-common-infra.png)

<figcaption>Common infrastructure team supports others</figcaption></figure></div>

This works, but organizational dynamics make these teams unstable
outside large, mature engineering departments.

As noted before, even modest engineering departments are probably
using five to eight languages. If libraries must be produced in even
half of those then the common infrastructure team must be staffed with
polyglots: developers who can write robust code in many languages.

There is a limited pool of extreme polyglots, and each other director
can fix a headcount shortage by poaching developers from the common
infrastructure team.

Temper, a single language that multiplies the effectiveness of modest
polyglots, solves this dynamic.

Temper makes common infrastructure teams viable and multiplies their
effectiveness.

## Why not embed virtual machines?

<div style="margin: 1em 0 1em 3em" markdown="1"><figure id="xkcd-lisp" markdown="1">

  ![Last night I drifted off while reading a Lisp book.&#10;Suddenly I was bathed in a blue light.&#10;At once, just like they said, I felt a great enlightenment.&#10;I saw the naked structure of Lisp code unrolled before me.&#10;The patterns and meta-patterns danced.&#10;Syntax faded, and I swam in the purity of quantified conception.&#10;Of ideas manifest.&#10;Truly, this is the language from which the gods wrought the Universe.&#10;God: &quot;No it's not.&quot;&#10;&quot;It's not.&quot;&#10;God: &quot;I mean, ostensibly yes.  Honestly, we hacked most of it together with Perl.&quot;](https://imgs.xkcd.com/comics/lisp.jpg)

<figcaption markdown="1">Glue languages make the world go round  (courtesy [xkcd.com/224/](https://xkcd.com/224/))</figcaption></figure></div>

Glue languages (e.g. JavaScript, Python, and Perl) are widely used to
connect components of multi-language systems:

> [Glue language](https://www.techopedia.com/definition/19608/glue-language):
> a programming language designed specifically to write and
> manage program and code, which connects together different software
> components. It enables interconnecting, support and the integration
> of software programs and components created using different
> programming languages and platforms.

Is Temper a glue language?  No.

<div style="margin: 1em 0 1em 3em" markdown="1"><figure id="glue-code" markdown="1">

  ![A main method in JavaScript with arrows out to a micro-service in Java, a child&#95;process invocation of bash, and a Wasm binary written in C++.  The Javascript file is embedded in a JavaScript engine which is embedded in a process.  The child&#95;process call passes through an ABI box from javascript to the process, and through a syscall box from the process to the outside.  An arrow from the Wasm box bounces back to a variable in the JavaScript main function indicating a result computed by the Wasm function was stored.](./images/glue-code.png)

<figcaption>Glue code manages and directs by <b>calling out</b></figcaption></figure></div>

Glue code manages and directs. It requests some work from a
microservice. It combines that work with something from a child
process. It loads WebAssembly code to do more work for it. It
integrates the work of others.  It's in charge.

Temper is not glue; it's more akin to grease or ball bearings. It
reduces friction and supports loads between components in sprawling,
organically growing, multi-language systems.  Code written in other
languages **calls into** it; it doesn't need to be in charge.

<div style="margin: 1em 0 1em 3em" markdown="1"><figure id="grease-code" markdown="1">

 ![An input temper file, &quot;MyLibrary.temper&quot;, at the top feeds into a cross-compiler to produce two outputs: a C-sharp output and a Python output.  Component one is a box around a C-sharp program which imports the C-sharp library compiled from Temper by doing &quot;import com.example.MyLibrary;&quot;.  Similarly, component two is a box around a Python program that imports the Python library compiled from Temper by doing &quot;from my_library import stuff&quot;.](./images/grease-code.png)

<figcaption>Grease code supports and smoothes the path by <b>working within</b></figcaption></figure></div>

From a common specification, the Temper compiler generates libraries
in various languages that load into different runtimes/processes/nodes.
Temper lets you define solutions to cross-cutting problems once and share
them among many parts of complex multi-language systems regardless of the
programming language they're written in.

In glue code, you control how the work gets divided up between
components. In Temper, you write the code that does the work on behalf
of many kinds of components.

## What is Temper?

In a nutshell, Temper is a general-purpose programming language
designed from the ground up to translate well to all the other
languages.

It's meant for producing libraries, not whole programs, so supporting
one more language doesn't require porting large libraries.

It has very low runtime requirements, so fits easily into existing
runtimes.

Temper has no engine or virtual machine with its own concept of
_value_ and _object_.  There's no barrier between a Temper library and
the code that loads it.  Temper meets the embedding language as it is.

A small team using Temper can support all the other developers in an
organization or community by factoring out common problems and sharing
common definitions.

With Temper, we can more efficiently build more reliable and
maintainable systems.
