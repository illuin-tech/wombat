/* Users page (admins only): grants and removes roles through /api/users/{id}/roles/{role}, then reloads the page. */
function userRoleErrorMessage(status) {
  switch (status) {
    case 400: return 'Unknown role.';
    case 401: return 'Your session has expired, reload the page to sign in again.';
    case 403: return 'You are not allowed to manage users anymore.';
    case 404: return 'This user no longer exists.';
    case 409: return 'You cannot remove your own admin role: ask another admin.';
    default:  return 'Unexpected error (' + status + '), try again.';
  }
}

function changeUserRole(button, method, question) {
  var userId = button.dataset.userId;
  var role = button.dataset.role;
  if (!window.confirm(question)) return;

  button.disabled = true;
  fetch('/api/users/' + encodeURIComponent(userId) + '/roles/' + encodeURIComponent(role), { method: method, headers: { 'Accept': 'application/json' } })
    .then(function(response) {
      if (!response.ok) throw new Error(userRoleErrorMessage(response.status));
      window.location.reload();
    })
    .catch(function(error) {
      button.disabled = false;
      window.alert(error.message);
    });
}

document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('.user-grant').forEach(function(button) {
    button.addEventListener('click', function() {
      changeUserRole(button, 'PUT', 'Grant the ' + button.dataset.role + ' role to ' + button.dataset.userName + '?');
    });
  });
  document.querySelectorAll('.user-role-remove').forEach(function(button) {
    button.addEventListener('click', function() {
      changeUserRole(button, 'DELETE', 'Remove the ' + button.dataset.role + ' role granted to ' + button.dataset.userName + '?');
    });
  });
});
