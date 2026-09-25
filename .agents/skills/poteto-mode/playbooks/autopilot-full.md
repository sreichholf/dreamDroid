# Independent PR autopilot

1. Inventory the independent PR/change queue and define merge criteria for each item.
2. Assign one owner per item. Owners build, verify, address review/CI, and maintain a clean head.
3. Before merge, obtain an independent verification verdict for that exact head.
4. Allow the owner to land only after the independent verdict and current PR state agree.
5. Reconcile the base after each merge and re-evaluate queued items for new conflicts or invalidated assumptions.
