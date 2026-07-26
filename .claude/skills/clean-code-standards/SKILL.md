---
name: clean-code-standards
description: Apply clean-code standards when writing, refactoring, or reviewing code. Use whenever creating new modules/classes/functions, refactoring or cleaning up existing code, reviewing a diff or PR, or when the user mentions clean code, SOLID, KISS, DRY, YAGNI, code smells, or maintainability.
---

# Clean Code Standards

Apply these principles when writing new code, refactoring, or reviewing changes.
Prefer the smallest change that satisfies the rule. When two principles conflict
(most often DRY vs KISS, or SOLID vs YAGNI), state the tension explicitly and
choose the simpler option unless the user says otherwise — do not silently pick.

## Clean Code Fundamentals
- Names reveal intent: a reader should understand a name without reading its body.
  No abbreviations that aren't domain-standard, no single letters except loop indices.
- Functions do one thing at one level of abstraction. If you need a comment to
  separate "sections" inside a function, those sections are separate functions.
- Keep functions short and arguments few. More than ~3 parameters usually signals a
  missing parameter object or a function doing too much.
- Comments explain *why*, never *what*. If a comment restates the code, delete it and
  fix the name instead. Delete commented-out code — that's what version control is for.
- No magic numbers or strings — name them as constants.
- Fail fast: validate inputs early and return/throw, rather than nesting the happy
  path inside conditionals.

## SOLID
- **S**ingle Responsibility: one reason to change per class/module. "And" in a
  description of what a class does is a smell.
- **O**pen/Closed: extend behavior via new code, not by editing tested code, when the
  extension point is real. Don't build speculative extension points (see YAGNI).
- **L**iskov Substitution: subtypes must honor the contract of their base — no
  strengthened preconditions or weakened postconditions, no throwing where the base
  doesn't.
- **I**nterface Segregation: many small role-based interfaces over one fat interface.
  A client shouldn't depend on methods it never calls.
- **D**ependency Inversion: depend on abstractions, inject dependencies. High-level
  policy shouldn't import low-level detail directly.

## KISS (Keep It Simple)
- The simplest solution that meets the *current* requirement wins. Reach for a design
  pattern only when the problem actually exhibits the shape that pattern solves.
- No cleverness for its own sake — clarity beats a one-liner. If a reviewer would have
  to pause to decode it, rewrite it plainly.

## DRY (Don't Repeat Yourself)
- Extract knowledge that is *genuinely the same*, not code that merely *looks* similar.
  Two blocks that are identical today but change for different reasons are NOT
  duplication — coupling them is a mistake. (Prefer a little duplication over the
  wrong abstraction.)
- DRY applies to knowledge — business rules, constants, validation — first; incidental
  textual similarity second.

## YAGNI (You Aren't Gonna Need It)
- Build for today's stated requirement. Do not add config, hooks, parameters,
  abstraction layers, or "flexibility" for a future that isn't in the requirement.
- A speculative interface with one implementation is a YAGNI violation — inline it
  until a second implementation actually exists.

## How to apply
- **Writing new code:** follow the above by default; no need to announce each rule.
- **Refactoring:** make behavior-preserving changes; call out which principle each
  change serves in one short phrase (e.g. "extract method — SRP"). Don't mix
  refactoring with behavior changes in the same step.
- **Reviewing:** report violations as `principle — file:line — the issue — the fix`,
  most impactful first. Distinguish must-fix (correctness, real duplication of a
  business rule) from nice-to-have (naming, minor structure).
- Never rewrite more than the task requires in the name of cleanliness — that itself
  violates KISS and YAGNI.