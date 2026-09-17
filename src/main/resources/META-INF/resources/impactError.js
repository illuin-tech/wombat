/* Loaded in <head>, so bind once the form exists. The submit routes through the shared
   appendDateParams so the range is converted to UTC and clamped exactly like the main report. */
document.addEventListener('DOMContentLoaded', function() {
  var form = document.getElementById('filterForm');
  if (!form) return;
  form.addEventListener('submit', function(e) {
    e.preventDefault();
    var params = appendDateParams(new URLSearchParams());
    window.location.href = '?' + params.toString();
  });
});
