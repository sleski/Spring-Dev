<%@ page pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html lang="de" dir="ltr">
<head>
  <title>Spring MVC start!</title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
  <script language="JavaScript" type="text/javascript" src="<c:url value="/static/jquery.min.js" />"></script>

  <script>
  $(document).ready(function(){
    $("p").click(function(){
      $(this).hide();
    });
  });
  </script>
</head>

<body>
	<h2>Hello Simple spring MVC!</h2>
	<br>



<p>If you click on me, I will disappear.</p>
<p>Click me away!</p>
<p>Click me too!</p>


</body>
</html>