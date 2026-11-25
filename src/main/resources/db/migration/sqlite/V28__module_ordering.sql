-- The place of a module among those it competes with, from 1 for the one tried first: only the family modules of a
-- family compete, so it is null for an asset-type module. Existing family rows get one at the next startup, in
-- detection order.
ALTER TABLE modules ADD COLUMN ordering INTEGER;
