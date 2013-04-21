<?php 
/* ridiculously simple service that takes the following parameters:
 * category_id: default for the "universe" pictures.
 * offset: start index
 * number: number of results to return 
 *
 * Returns the results in JSON along with a "is_remaining" flag letting the 
 * client application know that there is more data to grab.
 */
include_once 'database_connection.php';

$offset = isset($_GET['offset']) ? $_GET['offset'] : 0;
$number = isset($_GET['number']) ? $_GET['number'] : 20;
$category = isset($_GET['category_id']) ? $_GET['category_id'] : 10;

/* find out number remaining */
$sql = "select count(*) count from photos";
$dbconn = connect();
$result = $dbconn->query($sql);
$dbconn->close();
$row = mysqli_fetch_assoc($result);
$is_remaining = FALSE;
if($row['count'] - (offset + number) > 0) $is_remaining = TRUE;

$sql = "SELECT * FROM photos WHERE category_id = $category LIMIT $number OFFSET $offset";
$dbconn = connect();
$result = $dbconn->query($sql);
$photos = array();
while($photo = $result->fetch_assoc()) {
  $photos[] = array('photo' => $photo);
}  

echo json_encode(array('photos' => $photos, 'is_remaining' => $is_remaining));
?>
