<!DOCTYPE html>
<html lang="en">
    <head>
        <title>Access Denied</title>
    </head>
    <body>
        <p>Access Denied</p>
        <p>${reason}</p>
        <form method="post" action="/logout">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <button type="submit">Logout</button>
        </form>
    </body>
</html>
