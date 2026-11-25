-- The place of a module in the order of the modules, from 1: the family modules of a family are tried in that order.
-- Existing rows get one at the next startup, in detection order.
ALTER TABLE modules ADD COLUMN ordering INTEGER;
